import threading
from collections import defaultdict

class RateOrderObserver:
    def update(self, order):
        pass

class RateOrderSubject:
    def add_observer(self, observer):
        pass

    def notify_all(self, order):
        pass

class Solution:
    def __init__(self):
        self.helper = None
        self.restaurant_search_manager = None
        self.orders_manager = None

    def init(self, helper):
        self.helper = helper
        self.orders_manager = OrdersManager()
        self.restaurant_search_manager = RestaurantSearchManager(self.orders_manager)

    def order_food(self, order_id, restaurant_id, food_item_id):
        self.orders_manager.order_food(order_id, restaurant_id, food_item_id)

    def rate_order(self, order_id, rating):
        self.orders_manager.rate_order(order_id, rating)

    def get_top_restaurants_by_food(self, food_item_id):
        return self.restaurant_search_manager.get_restaurants_by_food(food_item_id, 20)

    def get_top_rated_restaurants(self):
        return self.restaurant_search_manager.get_top_rated_restaurants(20)

class OrdersManager(RateOrderSubject):
    def __init__(self):
        self.map = {}
        self.observers = []

    def order_food(self, order_id, restaurant_id, food_item_id):
        order = Order(order_id, restaurant_id, food_item_id, 0)
        self.map[order_id] = order

    def rate_order(self, order_id, rating):
        order = self.map[order_id]
        order.set_rating(rating)
        self.notify_all(order)

    def add_observer(self, observer):
        self.observers.append(observer)

    def notify_all(self, order):
        for observer in self.observers:
            observer.update(order)

class RestaurantSearchManager:
    def __init__(self, rate_order_subject):
        self.most_rated_restaurants = MostRatedRestaurants()
        self.most_rated_restaurants_by_food = MostRatedRestaurantsByFood()
        rate_order_subject.add_observer(self.most_rated_restaurants)
        rate_order_subject.add_observer(self.most_rated_restaurants_by_food)

    def get_restaurants_by_food(self, food_item_id, n):
        return self.most_rated_restaurants_by_food.get_restaurants(food_item_id, n)

    def get_top_rated_restaurants(self, n):
        return self.most_rated_restaurants.get_restaurants(n)

class MostRatedRestaurants(RateOrderObserver):
    def __init__(self):
        self.ratings_map = defaultdict(lambda: Rating(0, 0))
        self.rating_divisions = [set() for _ in range(42)]
        self.lock = threading.Lock()

    def update(self, order):
        with self.lock:
            rating = self.ratings_map[order.get_restaurant_id()]
            if rating.get_average_rating() >= 1.0:
                remove_set = self.rating_divisions[self.get_division_key(rating.get_average_rating())]
                remove_set.discard(order.get_restaurant_id())

            rating.add(order.get_rating())
            add_set = self.rating_divisions[self.get_division_key(rating.get_average_rating())]
            add_set.add(order.get_restaurant_id())
            self.ratings_map[order.get_restaurant_id()] = rating

    def get_division_key(self, rating):
        return int(rating * 10 - 10)

    def get_restaurants(self, n):
        restaurants = []
        for i in range(len(self.rating_divisions) - 1, -1, -1):
            if len(restaurants) >= n:
                break
            current_set = self.rating_divisions[i]
            sorted_list = sorted(current_set)
            restaurants.extend(sorted_list[:n - len(restaurants)])
        return restaurants

class MostRatedRestaurantsByFood(RateOrderObserver):
    def __init__(self):
        self.map = defaultdict(SortedSetWithLock)

    def get_rating(self, food_id, restaurant_id):
        return self.map[food_id].get(restaurant_id).get_average_rating()

    def update(self, order):
        set_with_lock = self.map[order.get_food_item_id()]
        new_rating = set_with_lock.get(order.get_restaurant_id())
        new_rating.add(order.get_rating())
        set_with_lock.update(order.get_restaurant_id(), new_rating)

    def get_restaurants(self, food_item_id, n):
        set_with_lock = self.map.get(food_item_id)
        if set_with_lock is None:
            return []
        return set_with_lock.get_ids(n)

class SortedSetWithLock:
    def __init__(self):
        self.ratings = defaultdict(lambda: Rating(0, 0))
        self.lock = threading.Lock()

    def update(self, id, new_value):
        with self.lock:
            self.ratings[id] = new_value

    def get(self, id):
        return self.ratings[id].copy()

    def get_ids(self, n):
        with self.lock:
            ids = sorted(self.ratings.keys(), key=lambda x: (-self.ratings[x].get_average_rating(), x))
        return ids[:n]

class Rating:
    def __init__(self, sum=0, count=0):
        self.sum = sum
        self.count = count
        self.lock = threading.Lock()

    def get_average_rating(self):
        with self.lock:
            if self.count <= 0:
                return 0.0
            rating = self.sum / self.count
            return round(rating, 1)

    def add(self, num):
        with self.lock:
            self.sum += num
            self.count += 1

    def copy(self):
        with self.lock:
            return Rating(self.sum, self.count)

class Order:
    def __init__(self, order_id, restaurant_id, food_item_id, rating):
        self.order_id = order_id
        self.restaurant_id = restaurant_id
        self.food_item_id = food_item_id
        self.rating = rating

    def __str__(self):
        return f"({self.order_id}, {self.food_item_id}, {self.restaurant_id}, {self.rating})"

    def set_rating(self, rating):
        self.rating = rating

    def get_order_id(self):
        return self.order_id

    def get_restaurant_id(self):
        return self.restaurant_id

    def get_food_item_id(self):
        return self.food_item_id

    def get_rating(self):
        return self.rating
