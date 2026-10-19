# Payment Gateway Strategy Selection in Java

#### Problem Statement
[https://codezym.com/question/307-payment-gateway-strategy-selection](https://codezym.com/question/307-payment-gateway-strategy-selection)


Use the **Strategy pattern** to give UPI and bank transfers their own payment logic. A map connects each payment method name to its strategy. The gateway selects that object for each call and returns its result. Strategy alone fits this problem because the payment behavior changes with the requested method.


## Start with a simple approach

We could put all UPI and bank-transfer checks inside `processPayment` using `if` and `else`. This works for two methods, but every new payment method adds more validation and processing branches to the gateway.


Separate strategies keep those rules together for each payment method and keep the gateway small.


## Use Strategy with a map

**`PaymentStrategy`** defines the common `pay` operation. The gateway can call it without knowing the selected payment method's rules.


**`UpiPaymentStrategy` and `BankTransferPaymentStrategy`** validate their own details and build their own success or failure result. They simulate processing by returning a string.


**`PaymentGateway`** stores the strategies in a `Map<String, PaymentStrategy>` backed by a `HashMap`. It looks up `paymentMethod`, returns an unsupported-method error if no strategy exists, and otherwise calls `pay` on the selected object.


The constructor creates the two strategy objects once. These objects keep no payment state, so the same objects can serve later calls.


A separate Factory is unnecessary here. The supported objects are simple to create once during setup, and the map already handles selection.


To add another payment method, implement `PaymentStrategy` and register its object in the map during setup. The existing strategies and `processPayment` need no changes.


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

The map selects the strategy registered for the requested method. Each strategy checks all the format rules for its own method and builds the required result. The gateway returns that result directly, so supported valid inputs succeed, supported invalid inputs fail, and unknown methods return the unsupported-method error.


## Complexity

Let `n` be the length of `paymentDetails`. Strategy lookup has expected constant cost under the bounded method-name length. Validation and building a successful response take **O(n)** time.


The two registered strategies and validation variables use **O(1)** extra space. The returned success string uses **O(n)** space.


## Complete Java solution

```java
import java.util.HashMap;
import java.util.Map;

interface PaymentStrategy {
    String pay(long amountInCents, String paymentDetails);
}

class UpiPaymentStrategy implements PaymentStrategy {
    @Override
    public String pay(long amountInCents, String paymentDetails) {
        int atIndex = paymentDetails.indexOf('@');

        // Require exactly one @ with a nonempty part on each side.
        if (atIndex <= 0
                || atIndex == paymentDetails.length() - 1
                || atIndex != paymentDetails.lastIndexOf('@')) {
            return "FAILED,INVALID_PAYMENT_DETAILS,UPI";
        }

        return "SUCCESS,UPI," + amountInCents + "," + paymentDetails;
    }
}

class BankTransferPaymentStrategy implements PaymentStrategy {
    @Override
    public String pay(long amountInCents, String paymentDetails) {
        int commaIndex = paymentDetails.indexOf(',');

        // Require one separator, a nonempty account and an 11-character IFSC.
        if (commaIndex <= 0
                || commaIndex != paymentDetails.lastIndexOf(',')
                || paymentDetails.length() - commaIndex - 1 != 11) {
            return "FAILED,INVALID_PAYMENT_DETAILS,BANK_TRANSFER";
        }

        // Check account digits without converting the account to a number.
        for (int index = 0; index < commaIndex; index++) {
            char digit = paymentDetails.charAt(index);
            if (digit < '0' || digit > '9') {
                return "FAILED,INVALID_PAYMENT_DETAILS,BANK_TRANSFER";
            }
        }

        return "SUCCESS,BANK_TRANSFER," + amountInCents + "," + paymentDetails;
    }
}

public class PaymentGateway {
    Map<String, PaymentStrategy> strategies;

    public PaymentGateway() {
        // Create the supported strategies once and reuse them for each call.
        strategies = new HashMap<>();
        strategies.put("UPI", new UpiPaymentStrategy());
        strategies.put("BANK_TRANSFER", new BankTransferPaymentStrategy());
    }

    public String processPayment(
            String paymentMethod, long amountInCents, String paymentDetails) {
        PaymentStrategy strategy = strategies.get(paymentMethod);

        // Resolve the method before checking method-specific details.
        if (strategy == null) {
            return "FAILED,UNSUPPORTED_PAYMENT_METHOD," + paymentMethod;
        }

        return strategy.pay(amountInCents, paymentDetails);
    }
}
```
