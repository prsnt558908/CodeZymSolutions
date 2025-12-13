from dataclasses import dataclass
from typing import List, Dict


@dataclass
class Item:
    # Internal representation of a catalog item
    price_per_unit: int
    units_available: int  # remaining stock


class ShoppingCart:
    UNAVAILABLE = "UNAVAILABLE"
    OUT_OF_STOCK = "OUT OF STOCK"
    SUCCESS = "SUCCESS"

    def __init__(self, items: List[str]):
        # Catalog: itemId -> Item (price + remaining stock)
        self.catalog: Dict[str, Item] = {}
        # Cart: itemId -> units in cart
        self.cart: Dict[str, int] = {}

        if items is not None:
            for row in items:
                if row is None:
                    continue
                row = row.strip()
                if not row:
                    continue

                # Each row: itemId,pricePerUnit,unitsAvailable
                parts = row.split(",")
                if len(parts) != 3:
                    # Problem statement guarantees valid input format,
                    # so we can safely skip invalid rows if any.
                    continue

                item_id = parts[0]
                price = int(parts[1])
                units = int(parts[2])

                self.catalog[item_id] = Item(price_per_unit=price, units_available=units)

    def addItem(self, itemId: str, count: int) -> str:
        item = self.catalog.get(itemId)
        if item is None:
            # Unknown item ID
            return self.UNAVAILABLE

        # Check remaining stock
        if item.units_available < count:
            return self.OUT_OF_STOCK

        # Reserve stock by decreasing available units
        item.units_available -= count

        # Add to cart (accumulate if already present)
        existing = self.cart.get(itemId, 0)
        self.cart[itemId] = existing + count

        return self.SUCCESS

    def viewCart(self) -> List[str]:
        result: List[str] = []
        if not self.cart:
            return result  # empty list

        # Sort itemIds lexicographically
        item_ids = sorted(self.cart.keys())

        for item_id in item_ids:
            count = self.cart[item_id]
            result.append(f"{item_id},{count}")

        return result

    def checkout(self) -> int:
        if not self.cart:
            return -1  # empty-cart checkout

        total = 0
        for item_id, count in self.cart.items():
            item = self.catalog.get(item_id)
            if item is not None:
                total += item.price_per_unit * count

        # Clear cart after checkout (catalog stock is NOT reset)
        self.cart.clear()

        return total
