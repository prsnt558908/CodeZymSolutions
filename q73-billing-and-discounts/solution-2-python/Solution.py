from dataclasses import dataclass, field
from typing import Dict, List, Optional, Set, Tuple


@dataclass
class _Bill:
    bill_id: str
    customer_id: str
    subtotal: int
    paid: bool = False
    applied_codes: Set[str] = field(default_factory=set)  # idempotent per code


class BillingSystem:
    def __init__(self):
        self._next_bill_num: int = 1
        self._bills: Dict[str, _Bill] = {}
        self._points: Dict[str, int] = {}

    def createBill(self, customerId, cartItems):
        if not isinstance(customerId, str) or customerId == "":
            return "ERROR"
        if not isinstance(cartItems, list) or len(cartItems) == 0:
            return "ERROR"

        subtotal = 0
        for s in cartItems:
            parsed = self._parse_cart_item(s)
            if parsed is None:
                return "ERROR"
            unit_price, quantity = parsed
            subtotal += unit_price * quantity  # fits in 64-bit by problem constraint

        bill_id = f"B{self._next_bill_num}"
        self._next_bill_num += 1

        if customerId not in self._points:
            self._points[customerId] = 0

        self._bills[bill_id] = _Bill(bill_id=bill_id, customer_id=customerId, subtotal=subtotal)
        return bill_id

    def applyDiscount(self, billId, discountCode):
        bill = self._bills.get(billId)
        if bill is None or bill.paid:
            return -1

        if discountCode in ("P10", "P20", "FLAT100", "REDEEM"):
            bill.applied_codes.add(discountCode)  # idempotent
        # unknown code ignored; still return current payable
        payable, _redeem_used = self._compute_payable_and_redeem(bill)
        return payable

    def payBill(self, billId, amountPaid):
        bill = self._bills.get(billId)
        if bill is None or bill.paid:
            return "ERROR"

        payable, redeem_used = self._compute_payable_and_redeem(bill)
        if amountPaid != payable:
            return "ERROR"

        # success: mark paid
        bill.paid = True

        # deduct redeemed points only on successful payment
        if "REDEEM" in bill.applied_codes:
            current = self._points.get(bill.customer_id, 0)
            current -= min(current, redeem_used)
            if current < 0:
                current = 0
            self._points[bill.customer_id] = current

        # earn points
        earned = payable // 100
        self._points[bill.customer_id] = self._points.get(bill.customer_id, 0) + earned

        total = self._points[bill.customer_id]
        level = self._level_from_points(total)

        return f"PAID|final={payable}|pointsEarned={earned}|totalPoints={total}|level={level}"

    # ---------------- helpers ----------------

    def _parse_cart_item(self, s: str) -> Optional[Tuple[int, int]]:
        if not isinstance(s, str):
            return None
        parts = s.split("|")
        if len(parts) != 3:
            return None
        item_name, unit_str, qty_str = parts
        if item_name == "":
            return None
        try:
            unit_price = int(unit_str)
            quantity = int(qty_str)
        except Exception:
            return None
        if unit_price < 0 or quantity <= 0:
            return None
        return unit_price, quantity

    def _compute_payable_and_redeem(self, bill: _Bill) -> Tuple[int, int]:
        subtotal = bill.subtotal

        # effective percent: max(P10, P20)
        percent = 0
        if "P10" in bill.applied_codes:
            percent = max(percent, 10)
        if "P20" in bill.applied_codes:
            percent = max(percent, 20)

        percent_discount = (subtotal * percent) // 100  # floor
        payable = subtotal - percent_discount

        # flat discount after percent (only if subtotal >= 500)
        if "FLAT100" in bill.applied_codes and subtotal >= 500:
            payable -= 100

        if payable < 0:
            payable = 0

        redeem_used = 0
        if "REDEEM" in bill.applied_codes:
            cap = (payable * 20) // 100  # floor
            pts = self._points.get(bill.customer_id, 0)
            redeem_used = min(pts, cap)
            payable -= redeem_used
            if payable < 0:
                payable = 0

        return payable, redeem_used

    def _level_from_points(self, points: int) -> str:
        if points >= 2000:
            return "PLATINUM"
        if points >= 500:
            return "GOLD"
        if points >= 100:
            return "SILVER"
        return "BRONZE"
