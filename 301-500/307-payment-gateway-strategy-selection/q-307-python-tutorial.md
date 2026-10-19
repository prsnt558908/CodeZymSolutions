# Payment Gateway Strategy Selection in Python

#### Problem Statement
[https://codezym.com/question/307-payment-gateway-strategy-selection](https://codezym.com/question/307-payment-gateway-strategy-selection)


Use the **Strategy pattern** to give UPI and bank transfers their own payment logic. A dictionary connects each payment method name to its strategy. The gateway selects that object for each call and returns its result. Strategy alone fits this problem because the payment behavior changes with the requested method.


## Start with a simple approach

We could put all UPI and bank-transfer checks inside `processPayment` using `if` and `else`. This works for two methods, but every new payment method adds more validation and processing branches to the gateway.


Separate strategies keep those rules together for each payment method and keep the gateway small.


## Use Strategy with a dictionary

**`PaymentStrategy`** defines the common `pay` operation. The gateway can call it without knowing the selected payment method's rules.


**`UpiPaymentStrategy` and `BankTransferPaymentStrategy`** validate their own details and build their own success or failure result. They simulate processing by returning a string.


**`PaymentGateway`** stores the strategies in a dictionary from method names to strategy objects. It looks up `paymentMethod`, returns an unsupported-method error if no strategy exists, and otherwise calls `pay` on the selected object.


`PaymentGateway` is a dataclass. `field(default_factory=dict, init=False)` gives each gateway its own dictionary while keeping the constructor argument-free. `__post_init__` creates the two strategies once. They keep no payment state and can be reused.


A separate Factory is unnecessary here. The supported objects are simple to create once during setup, and the dictionary already handles selection.


To add another payment method, implement `PaymentStrategy` and register its object in the dictionary during setup. The existing strategies and `processPayment` need no changes.


## Validate the details

- **UPI:** Require exactly one `@`, with at least one character on each side.
- **Bank transfer:** Require exactly one comma, a nonempty account containing only digits `0` through `9`, and an IFSC field of exactly 11 characters.
- **Method selection:** Match `UPI` and `BANK_TRANSFER` exactly. For example, `upi` is unsupported.


The IFSC validation uses the length rule given in the statement. Keep the original details in the success response. Checking account characters directly preserves leading zeros and also handles account numbers too long for a numeric type.


The constraints already guarantee valid input lengths and a positive amount within the allowed range. Payment-specific validation belongs inside each strategy.


## Walk through a payment

For method `BANK_TRANSFER`, amount `84500`, and details `123456789012,HDFC0001234`, the gateway finds `BankTransferPaymentStrategy`.


The strategy finds one comma, checks that every account character is a digit, and checks the 11-character IFSC. It returns `SUCCESS,BANK_TRANSFER,84500,123456789012,HDFC0001234`.


Details such as `abc,HDFC0001234` produce `FAILED,INVALID_PAYMENT_DETAILS,BANK_TRANSFER`. Method `CARD` produces `FAILED,UNSUPPORTED_PAYMENT_METHOD,CARD` before any payment details are checked.


## Why this works

The dictionary selects the strategy registered for the requested method. Each strategy checks all the format rules for its own method and builds the required result. The gateway returns that result directly, so supported valid inputs succeed, supported invalid inputs fail, and unknown methods return the unsupported-method error.


## Complexity

Let `n` be the length of `paymentDetails`. Strategy lookup has expected constant cost under the bounded method-name length. Validation and building a successful response take **O(n)** time.


The two registered strategies and validation variables use **O(1)** extra space. The returned success string uses **O(n)** space.


## Complete Python solution

```python
from abc import ABC, abstractmethod
from dataclasses import dataclass, field


class PaymentStrategy(ABC):
    @abstractmethod
    def pay(self, amountInCents: int, paymentDetails: str) -> str:
        pass


class UpiPaymentStrategy(PaymentStrategy):
    def pay(self, amountInCents: int, paymentDetails: str) -> str:
        atIndex = paymentDetails.find("@")

        # Require exactly one @ with a nonempty part on each side.
        if (
            atIndex <= 0
            or atIndex == len(paymentDetails) - 1
            or atIndex != paymentDetails.rfind("@")
        ):
            return "FAILED,INVALID_PAYMENT_DETAILS,UPI"

        return f"SUCCESS,UPI,{amountInCents},{paymentDetails}"


class BankTransferPaymentStrategy(PaymentStrategy):
    def pay(self, amountInCents: int, paymentDetails: str) -> str:
        commaIndex = paymentDetails.find(",")

        # Require one separator, a nonempty account and an 11-character IFSC.
        if (
            commaIndex <= 0
            or commaIndex != paymentDetails.rfind(",")
            or len(paymentDetails) - commaIndex - 1 != 11
        ):
            return "FAILED,INVALID_PAYMENT_DETAILS,BANK_TRANSFER"

        # Check account digits without converting the account to a number.
        for index in range(commaIndex):
            digit = paymentDetails[index]
            if digit < "0" or digit > "9":
                return "FAILED,INVALID_PAYMENT_DETAILS,BANK_TRANSFER"

        return f"SUCCESS,BANK_TRANSFER,{amountInCents},{paymentDetails}"


@dataclass
class PaymentGateway:
    strategies: dict[str, PaymentStrategy] = field(default_factory=dict, init=False)

    def __post_init__(self):
        # Create the supported strategies once and reuse them for each call.
        self.strategies["UPI"] = UpiPaymentStrategy()
        self.strategies["BANK_TRANSFER"] = BankTransferPaymentStrategy()

    def processPayment(
        self, paymentMethod: str, amountInCents: int, paymentDetails: str
    ) -> str:
        strategy = self.strategies.get(paymentMethod)

        # Resolve the method before checking method-specific details.
        if strategy is None:
            return f"FAILED,UNSUPPORTED_PAYMENT_METHOD,{paymentMethod}"

        return strategy.pay(amountInCents, paymentDetails)
```
