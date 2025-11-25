from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from typing import Dict, List


@dataclass
class Subscription:
    start_date: date
    plan_id: str


class CostExplorer:
    """
    In-memory CostExplorer that tracks:
    1) products + plans (monthly prices)
    2) customer subscriptions to a product plan starting from a date

    Rules recap:
    - addProduct overwrites the entire plan list for that product.
    - If a plan is removed later, its price becomes 0 for all subscribers.
    - If subscription starts on any day of a month, customer pays full month.
    - monthlyCost(customer, year) returns 12-month costs for that calendar year.
    - annualCost is the sum of those 12 months.
    """

    def __init__(self):
        # productName -> {planId -> monthlyPrice}
        self.product_plans: Dict[str, Dict[str, int]] = {}
        # customerId -> {productName -> Subscription}
        self.customer_subscriptions: Dict[str, Dict[str, Subscription]] = {}

    def addProduct(self, productName: str, plans: List[str]) -> None:
        """
        Adds/updates a product and its plans.
        plans are strings: "PLANID,monthlyPrice"
        Re-adding same product overwrites all old plans.
        """
        new_plans: Dict[str, int] = {}
        if plans:
            for p in plans:
                if not p:
                    continue
                parts = p.split(",")
                if len(parts) != 2:
                    continue  # inputs said valid, but safe-guard
                plan_id = parts[0].strip()
                price = int(parts[1].strip())
                new_plans[plan_id] = price

        # overwrite existing product's plans
        self.product_plans[productName] = new_plans

    def subscribe(self, customerId: str, startDate: str, productName: str, planId: str) -> None:
        """
        Subscribe/update a customer's subscription to a product.
        If already subscribed to same product, overwrite that subscription
        (startDate & planId).
        """
        sd = date.fromisoformat(startDate)  # YYYY-MM-DD

        subs_for_customer = self.customer_subscriptions.setdefault(customerId, {})
        subs_for_customer[productName] = Subscription(sd, planId)

    def monthlyCost(self, customerId: str, year: int) -> List[int]:
        """
        For given customer and year, compute cost per month.
        Returns 12 integers: Jan..Dec.
        """
        monthly = [0] * 12

        subs_for_customer = self.customer_subscriptions.get(customerId)
        if not subs_for_customer:
            return monthly

        for product_name, sub in subs_for_customer.items():
            price = self._get_monthly_price(product_name, sub.plan_id)  # 0 if removed
            if price == 0:
                continue  # removed plan or product => no contribution

            sd = sub.start_date
            start_year = sd.year

            if start_year > year:
                continue  # starts after this year => contributes 0

            if start_year < year:
                start_month_index = 0  # charge all months
            else:
                start_month_index = sd.month - 1  # Jan=0

            for m in range(start_month_index, 12):
                monthly[m] += price

        return monthly

    def annualCost(self, customerId: str, year: int) -> int:
        """
        Annual cost is sum of 12 monthly costs for that year.
        """
        return sum(self.monthlyCost(customerId, year))

    # ---- helpers ----

    def _get_monthly_price(self, productName: str, planId: str) -> int:
        plans = self.product_plans.get(productName)
        if not plans:
            return 0
        return plans.get(planId, 0)
