package uz.salvadore.orderservice.exception;

public class PaymentChargeException extends RuntimeException {
  String code;
  String message;
  public PaymentChargeException(String code, String message) {
    super(message);
    this.code = code;
    this.message = message;
  }
  public PaymentChargeException() {
    this.code = "30000";
    this.message = "Payment charge failed";
  }
  public String getCode() {
    return code;
  }
  public void setCode(String code) {
    this.code = code;
  }
  public String getMessage() {
    return message;
  }
}
