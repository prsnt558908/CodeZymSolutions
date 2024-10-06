
# problem statement: https://codezym.com/question/11
from collections import defaultdict, deque

class Solution:
    def __init__(self):
        self.floors_count = 0
        self.lifts_count = 0
        self.lifts_capacity = 0
        self.helper = None
        self.lifts = []

    def init(self, floors, lifts, lifts_capacity, helper):
        """
        Initializes the elevator system.
        Args:
            floors (int): The number of floors in the building.
            lifts (int): The number of lifts in the system.
            lifts_capacity (int): The maximum number of people per lift.
            helper (Helper11): Helper class for printing and other actions.
        """
        self.floors_count = floors
        self.lifts_count = lifts
        self.lifts_capacity = lifts_capacity
        self.helper = helper
        self.lifts = [Lift(floors, lifts_capacity) for _ in range(lifts)]
        # self.helper.println("Lift system initialized ...")

    def request_lift(self, floor, direction):
        """
        User presses the outside UP or DOWN button outside the lift.
        Args:
            floor (int): The current floor.
            direction (str): The direction ('U' for up or 'D' for down).
        
        Returns:
            int: Index of the selected lift or -1 if no lift is available.
        """
        lift_index = -1
        time_taken = -1
        for i, lift in enumerate(self.lifts):
            time = lift.get_time_to_reach_floor(floor, direction)
            if time < 0 or lift.count_people(floor, direction) >= self.lifts_capacity:
                continue
            if time_taken < 0 or time < time_taken:
                time_taken = time
                lift_index = i
        if lift_index >= 0:
            self.lifts[lift_index].add_incoming_request(floor, direction)
        return lift_index

    def press_floor_button_in_lift(self, lift_index, floor):
        """
        User presses the floor button inside the lift.
        Args:
            lift_index (int): Index of the lift.
            floor (int): The floor button pressed.
        """
        lift = self.lifts[lift_index]
        lift.add_outgoing_request(floor, lift.get_move_direction())

    def get_lift_state(self, lift_index):
        """
        Returns the current state of the lift.
        Args:
            lift_index (int): Index of the lift.
        
        Returns:
            str: String representation of the lift's state.
        """
        if lift_index < 0 or lift_index >= len(self.lifts):
            return ""
        lift = self.lifts[lift_index]
        return f"{lift.get_current_floor()}-{lift.get_move_direction()}-{lift.get_current_people_count()}"

    def tick(self):
        """
        This method is called every second to update the lift states.
        """
        for lift in self.lifts:
            lift.tick()

class Lift:
    def __init__(self, floors, capacity):
        self.current_floor = 0
        self.floors = floors
        self.capacity = capacity
        self.incoming_requests_count = set()
        self.outgoing_requests_count = defaultdict(int)
        self.moving_up_state = MovingUpState(self)
        self.moving_down_state = MovingDownState(self)
        self.idle_state = IdleState(self)
        self.moving_up_to_pick_first = MovingUpToPickFirstState(self)
        self.moving_down_to_pick_first = MovingDownToPickFirstState(self)
        self.state = self.idle_state

    def get_current_people_count(self):
        return sum(self.outgoing_requests_count.values())

    def get_time_to_reach_floor(self, floor, direction):
        return self.state.get_time_to_reach_floor(floor, direction)

    def add_incoming_request(self, floor, direction):
        if self.state.get_direction() == 'I':
            if floor == self.current_floor:
                self.set_state(direction)
            else:
                if floor > self.current_floor:
                    self.state = self.moving_up_state if direction == 'U' else self.moving_up_to_pick_first
                else:
                    self.state = self.moving_down_state if direction == 'D' else self.moving_down_to_pick_first
        self.incoming_requests_count.add(floor)

    def add_outgoing_request(self, floor, direction):
        self.outgoing_requests_count[floor] += 1

    def count_people(self, floor, direction):
        return self.state.count_people(floor, direction)

    def get_move_direction(self):
        return self.state.get_direction()

    def get_current_floor(self):
        return self.current_floor

    def tick(self):
        self.state.tick()
        if not self.outgoing_requests_count and not self.incoming_requests_count:
            self.set_state('I')

    def set_state(self, direction):
        if direction == 'U':
            self.state = self.moving_up_state
        elif direction == 'D':
            self.state = self.moving_down_state
        else:
            self.state = self.idle_state

    def set_current_floor(self, current_floor):
        self.current_floor = current_floor


class LiftState:
    def __init__(self, lift):
        self.lift = lift

    def get_direction(self):
        return 'I'

    def get_time_to_reach_floor(self, floor, direction):
        return 0

    def count_people(self, floor, direction):
        return 0

    def tick(self):
        pass


class MovingUpState(LiftState):
    def get_direction(self):
        return 'U'

    def get_time_to_reach_floor(self, floor, direction):
        if direction != 'U' or floor < self.lift.get_current_floor():
            return -1
        return floor - self.lift.get_current_floor()

    def count_people(self, floor, direction):
        if direction != 'U':
            return 0
        return sum(v for f, v in self.lift.outgoing_requests_count.items() if f > floor)

    def tick(self):
        self.lift.incoming_requests_count.discard(self.lift.get_current_floor())
        # check if idle state has been achieved i.e. if there were requests on previous floor but no one entered
        if not self.lift.incoming_requests_count and not self.lift.outgoing_requests_count:
            return
        self.lift.set_current_floor(self.lift.get_current_floor() + 1)
        self.lift.outgoing_requests_count.pop(self.lift.get_current_floor(), None)


class MovingDownState(LiftState):
    def get_direction(self):
        return 'D'

    def get_time_to_reach_floor(self, floor, direction):
        if direction != 'D' or floor > self.lift.get_current_floor():
            return -1
        return self.lift.get_current_floor() - floor

    def count_people(self, floor, direction):
        if direction != 'D':
            return 0
        return sum(v for f, v in self.lift.outgoing_requests_count.items() if f < floor)

    def tick(self):
        self.lift.incoming_requests_count.discard(self.lift.get_current_floor())
        # check if idle state has been achieved i.e. if there were requests on previous floor but no one entered
        if not self.lift.incoming_requests_count and not self.lift.outgoing_requests_count:
            return
        self.lift.set_current_floor(self.lift.get_current_floor() - 1)
        self.lift.outgoing_requests_count.pop(self.lift.get_current_floor(), None)


class IdleState(LiftState):
    def get_direction(self):
        return 'I'

    def get_time_to_reach_floor(self, floor, direction):
        return abs(floor - self.lift.get_current_floor())


class MovingUpToPickFirstState(LiftState):
    def get_direction(self):
        return 'U'

    def get_time_to_reach_floor(self, floor, direction):
        next_stop = self.next_stop()
        if direction != 'D' or floor > next_stop:
            return -1
        return next_stop - self.lift.get_current_floor() + next_stop - floor

    def next_stop(self):
        return max(self.lift.incoming_requests_count, default=-1)

    def tick(self):
        self.lift.set_current_floor(self.lift.get_current_floor() + 1)
        if self.lift.get_current_floor() == self.next_stop():
            self.lift.set_state('D')


class MovingDownToPickFirstState(LiftState):
    def get_direction(self):
        return 'D'

    def get_time_to_reach_floor(self, floor, direction):
        next_stop = self.next_stop()
        if direction != 'U' or floor < next_stop:
            return -1
        if next_stop < 0:
            next_stop = floor
        return self.lift.get_current_floor() - next_stop + floor - next_stop

    def next_stop(self):
        return min(self.lift.incoming_requests_count, default=-1)

    def tick(self):
        self.lift.set_current_floor(self.lift.get_current_floor() - 1)
        if self.lift.get_current_floor() == self.next_stop():
            self.lift.set_state('U')
