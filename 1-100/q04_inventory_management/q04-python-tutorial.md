# Design Order and Inventory System in Python for Low Level Design Interviews

Problem Statement: https://codezym.com/question/4-design-order-and-inventory-system

In this solution, we divide the system into small classes where each class has one clear responsibility. `Seller` stores seller-related details, `Inventory` stores product counts, `OrderController` validates and creates orders, and `Solution` acts as the main entry point expected by the online judge. This follows a simple controller-based design, which makes the code easier to understand, test, and extend in a low level design interview.

## Video Explanation (but for Java)

[![Design Order and Inventory System Solution](https://img.youtube.com/vi/VtBL_NNa7hs/hqdefault.jpg)](https://www.youtube.com/watch?v=VtBL_NNa7hs)

YouTube Video : https://www.youtube.com/watch?v=VtBL_NNa7hs

## Requirements

We need to design an order and inventory management system for an e-commerce platform.

The system should support:

1. Creating sellers with serviceable pincodes and supported payment modes.
2. Adding inventory for a seller.
3. Getting inventory count for a seller and product.
4. Creating an order after validating pincode, payment mode, and inventory availability.

## High Level Design

The solution is divided into these main parts:

- `Inventory` keeps product count details for a seller.
- `InventoryController` acts as a wrapper around `Inventory`.
- `Seller` stores seller details and owns its own inventory controller.
- `SellerController` manages all sellers.
- `Order` stores order details.
- `OrderController` validates and creates orders.
- `Solution` exposes the final methods required by the problem statement.

## Why Inventory Class Is Needed

Each seller has their own warehouse inventory. So, instead of keeping all inventory data directly inside the seller class, we keep it inside a separate `Inventory` class.

This keeps inventory-related logic in one place:

- add product count
- get product count
- check if enough inventory exists
- remove product count after an order is placed

Internally, we use a dictionary:

```python
product_id_vs_count = {}
```

Here, the key is `product_id` and the value is the available count of that product.

## Why Seller Class Is Needed

A seller has three important pieces of information:

- seller id
- serviceable pincodes
- supported payment modes

A seller also owns an inventory controller because each seller has their own inventory.

So, whenever we want to add inventory or remove inventory for a seller, we call methods through that seller object.

## Why SellerController Is Needed

`SellerController` manages all sellers in the system.

It stores sellers in a list and provides helper methods to:

- create a seller
- find a seller by seller id
- add product inventory for a seller
- check seller pincode availability
- check seller payment mode availability
- check seller product inventory availability

This keeps seller search and seller operations outside the main `Solution` class.

## Why OrderController Is Needed

`OrderController` contains the main order placement flow.

When a customer creates an order, we validate things in this order:

1. Check whether seller delivers to the destination pincode.
2. Check whether seller supports the selected payment mode.
3. Check whether seller has enough product inventory.
4. If all checks pass, create the order.
5. Reduce inventory.
6. Store the order.
7. Return `"order placed"`.

If any validation fails, we return the correct error message.

## Order Creation Flow

For this method:

```python
create_order(order_id, destination_pincode, seller_id, product_id, product_count, payment_mode)
```

The flow is:

```text
Check pincode
    ↓
Check payment mode
    ↓
Check inventory count
    ↓
Create order
    ↓
Reduce inventory
    ↓
Return order placed
```

## Complete Python Code

```python
class Inventory:
    def __init__(self):
        # product_id -> available product count
        self.product_id_vs_count = {}

    def add_product(self, product_id, delta):
        # Add delta count to existing product inventory.
        self.product_id_vs_count[product_id] = self.product_id_vs_count.get(product_id, 0) + delta
        return "inventory added"

    def get_product(self, product_id):
        # Return 0 if product is not available in inventory.
        return self.product_id_vs_count.get(product_id, 0)

    def check_product_count_availability(self, product_id, product_count):
        # Check whether required product count is available.
        available_prod_count = self.product_id_vs_count.get(product_id, 0)
        if available_prod_count >= product_count:
            return True
        return False

    def remove_product(self, product_id, product_count):
        # Reduce product count after order is successfully placed.
        self.product_id_vs_count[product_id] -= product_count


class InventoryController:
    def __init__(self):
        self.inventory = Inventory()

    def add_product(self, product_id, delta):
        return self.inventory.add_product(product_id, delta)

    def get_product(self, product_id):
        return self.inventory.get_product(product_id)

    def check_product_count_availability(self, product_id, product_count):
        return self.inventory.check_product_count_availability(product_id, product_count)

    def remove_product(self, product_id, product_count):
        return self.inventory.remove_product(product_id, product_count)


class Seller:
    def __init__(self, seller_id, pincodes, payment_modes):
        self.seller_id = seller_id
        self.pincodes = pincodes
        self.payment_modes = payment_modes

        # Each seller has their own inventory.
        self.inventory_controller = InventoryController()

    def inventory_add_product(self, product_id, delta):
        return self.inventory_controller.add_product(product_id, delta)

    def inventory_get_product(self, product_id):
        return self.inventory_controller.get_product(product_id)

    def inventory_remove_product(self, product_id, product_count):
        return self.inventory_controller.remove_product(product_id, product_count)

    def check_payment_mode_availability(self, payment_mode):
        if payment_mode in self.payment_modes:
            return True
        return False

    def check_pincode_availability(self, pincode):
        if pincode in self.pincodes:
            return True
        return False

    def inventory_check_product_count_availability(self, product_id, product_count):
        return self.inventory_controller.check_product_count_availability(product_id, product_count)

    def __str__(self):
        return f"seller_id : {self.seller_id}"


class SellerController:
    def __init__(self, helper):
        self.helper = helper

        # Stores all sellers in the system.
        self.sellers = []

    def create_seller(self, seller_id, serviceable_pincodes, payment_modes):
        seller = Seller(seller_id, serviceable_pincodes, payment_modes)
        self.sellers.append(seller)

    def seller_add_product(self, seller_id, product_id, delta):
        seller = next((seller for seller in self.sellers if seller.seller_id == seller_id), None)
        return seller.inventory_add_product(product_id, delta)

    def seller_get_product(self, seller_id, product_id):
        seller = next((seller for seller in self.sellers if seller.seller_id == seller_id), None)
        return seller.inventory_get_product(product_id)

    def seller_remove_product(self, seller_id, product_id, product_count):
        seller = next((seller for seller in self.sellers if seller.seller_id == seller_id), None)
        return seller.inventory_remove_product(product_id, product_count)

    def seller_check_payment_mode_availability(self, seller_id, payment_mode):
        seller = next((seller for seller in self.sellers if seller.seller_id == seller_id), None)
        return seller.check_payment_mode_availability(payment_mode)

    def seller_check_pincode_availability(self, seller_id, pincode):
        seller = next((seller for seller in self.sellers if seller.seller_id == seller_id), None)
        return seller.check_pincode_availability(pincode)

    def seller_check_product_count_availability(self, seller_id, product_id, product_count):
        seller = next((seller for seller in self.sellers if seller.seller_id == seller_id), None)
        return seller.inventory_check_product_count_availability(product_id, product_count)


class Order:
    def __init__(self, order_id, pincode, seller_id, product_id, product_count, payment_mode):
        self.order_id = order_id
        self.pincode = pincode
        self.seller_id = seller_id
        self.product_id = product_id
        self.products_count = product_count
        self.payment_mode = payment_mode


class OrderController:
    def __init__(self, helper, seller_controller):
        self.helper = helper
        self.orders = []
        self.seller_controller = seller_controller

    def create_order(self, order_id, destination_pincode, seller_id, product_id, product_count, payment_mode):
        # First check if seller can deliver to the destination pincode.
        if not self.seller_controller.seller_check_pincode_availability(seller_id, destination_pincode):
            return "pincode unserviceable"

        # Then check if seller supports the given payment mode.
        if not self.seller_controller.seller_check_payment_mode_availability(seller_id, payment_mode):
            return "payment mode not supported"

        # Then check if seller has enough inventory.
        if not self.seller_controller.seller_check_product_count_availability(seller_id, product_id, product_count):
            return "insufficient product inventory"

        # Create order only after all validations pass.
        order = Order(order_id, destination_pincode, seller_id, product_id, product_count, payment_mode)

        # Reduce inventory after successful order creation.
        self.seller_controller.seller_remove_product(seller_id, product_id, product_count)

        # Store order in the system.
        self.orders.append(order)

        return "order placed"


class Solution:
    def init(self, helper, products_count):
        self.helper = helper
        self.seller_controller = SellerController(self.helper)
        self.order_controller = OrderController(self.helper, self.seller_controller)

    def create_seller(self, seller_id, serviceable_pincodes, payment_modes):
        self.seller_controller.create_seller(seller_id, serviceable_pincodes, payment_modes)

    def add_inventory(self, product_id, seller_id, delta):
        self.seller_controller.seller_add_product(seller_id, product_id, delta)

    def get_inventory(self, product_id, seller_id):
        return self.seller_controller.seller_get_product(seller_id, product_id)

    def create_order(self, order_id, destination_pincode, seller_id, product_id, product_count, payment_mode):
        return self.order_controller.create_order(
            order_id,
            destination_pincode,
            seller_id,
            product_id,
            product_count,
            payment_mode
        )
```

## Summary

This solution keeps the design simple by separating responsibilities into different classes.

`SellerController` handles seller-related operations, `Inventory` handles product count, and `OrderController` handles order validation and placement.

This makes the code easy to explain in interviews and easy to extend later if we want to add more features like returns, order cancellation, product catalog, or seller ratings.
