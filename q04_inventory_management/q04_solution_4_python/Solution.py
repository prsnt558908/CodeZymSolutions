from collections import defaultdict

class Solution:
    def __init__(self):
        self.helper = None
        self.seller_manager = SellerManager()
        self.inventory_manager = InventoryManager()
        self.products_count = 0

    def init(self, helper, products_count):
        self.helper = helper
        self.products_count = products_count
        # self.helper.println("Ecommerce orders module initialized")

    def create_seller(self, seller_id, serviceable_pincodes, payment_modes):
        self.seller_manager.create_seller(seller_id, serviceable_pincodes, payment_modes)

    def add_inventory(self, product_id, seller_id, delta):
        if not self.seller_manager.get_seller(seller_id):
            return  # "seller doesn't exist"
        self.inventory_manager.add_inventory(product_id, seller_id, delta)

    def get_inventory(self, product_id, seller_id):
        #return 1
        return self.inventory_manager.get_inventory(product_id, seller_id)

    def create_order(self, order_id, destination_pincode, seller_id, product_id, product_count, payment_mode):
        seller = self.seller_manager.get_seller(seller_id)
        if not seller:
            return "seller doesn't exist"
        if not seller.serves_pincode(destination_pincode):
            return "pincode unserviceable"
        if not seller.supports_payment_type(payment_mode):
            return "payment mode not supported"
        reduced = self.inventory_manager.reduce_inventory(product_id, seller_id, product_count)
        return "order placed" if reduced else "insufficient product inventory"

class Seller:
    def __init__(self, serviceable_pincodes, seller_payment_modes):
        self.serviceable_pincodes = set(serviceable_pincodes)
        self.seller_payment_modes = set(seller_payment_modes)

    def serves_pincode(self, pincode):
        return pincode is not None and pincode in self.serviceable_pincodes

    def supports_payment_type(self, payment_type):
        return payment_type is not None and payment_type in self.seller_payment_modes


class SellerManager:
    def __init__(self):
        self.sellers = {}

    def create_seller(self, seller_id, serviceable_pincodes, payment_modes):
        self.sellers[seller_id] = Seller(serviceable_pincodes, payment_modes)

    def get_seller(self, seller_id):
        return self.sellers.get(seller_id)


class InventoryManager:
    def __init__(self):
        # Nested dictionary: product_id -> seller_id -> product_count
        self.product_inventory = defaultdict(lambda: defaultdict(int))

    def add_inventory(self, product_id, seller_id, delta):
        self.product_inventory[product_id][seller_id] += delta

    def reduce_inventory(self, product_id, seller_id, delta):
        if product_id not in self.product_inventory or seller_id not in self.product_inventory[product_id]:
            return False
        if self.product_inventory[product_id][seller_id] < delta:
            return False
        self.product_inventory[product_id][seller_id] -= delta
        return True

    def get_inventory(self, product_id, seller_id):
        return self.product_inventory.get(product_id, {}).get(seller_id, 0)


# Uncomment for testing in a local environment
"""
class Helper04:
    def print(self, s):
        print(s, end='')

    def println(self, s):
        print(s)
"""
