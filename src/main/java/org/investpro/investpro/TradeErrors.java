package org.investpro.investpro;

public class TradeErrors extends Throwable {

    private String errorCauseClassStackTrace;
    private String errorCauseClass;
    private String errorClass;
    private StackTraceElement[] errorStackTrace;
    private String errorMessage;
    private Throwable errorCause;
    private Object errorDescription;
    private StackTraceElement[] errorCauseStackTrace;

    public TradeErrors() {
        super();
    }

    public TradeErrors(String message) {
        super(message);
    }

    public TradeErrors(String message, Throwable cause) {
        super(message, cause);

        if (cause instanceof Error error) {

            this.errorMessage = error.getMessage();

            this.errorCause = error.getCause();
            this.errorStackTrace = error.getStackTrace();
            this.errorClass = error.getClass().getName();
            this.errorCauseClass = error.getCause().getClass().getName();
            this.errorCauseStackTrace = error.getCause().getStackTrace();
            this.errorCauseClassStackTrace = error.getCause().getClass().getName();
            this.errorCauseStackTrace = error.getCause().getStackTrace();
        }

    }

    public TradeErrors(String message, Throwable cause, Object errorDescription) {
        super(message, cause);

        if (cause instanceof Error error) {
            this.errorMessage = error.getMessage();
        }

        this.errorCause = cause;
        this.errorDescription = errorDescription;

        this.errorCauseClass = cause.getClass().getName();

    }

    public String getErrorCauseClassStackTrace() {
        return errorCauseClassStackTrace;
    }

    public void setErrorCauseClassStackTrace(String errorCauseClassStackTrace) {
        this.errorCauseClassStackTrace = errorCauseClassStackTrace;
    }

    public String getErrorCauseClass() {
        return errorCauseClass;
    }

    public void setErrorCauseClass(String errorCauseClass) {
        this.errorCauseClass = errorCauseClass;
    }

    public String getErrorClass() {
        return errorClass;
    }

    public void setErrorClass(String errorClass) {
        this.errorClass = errorClass;
    }

    public StackTraceElement[] getErrorStackTrace() {
        return errorStackTrace;
    }

    public void setErrorStackTrace(StackTraceElement[] errorStackTrace) {
        this.errorStackTrace = errorStackTrace;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Throwable getErrorCause() {
        return errorCause;
    }

    public void setErrorCause(Throwable errorCause) {
        this.errorCause = errorCause;
    }

    public Object getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(Object errorDescription) {
        this.errorDescription = errorDescription;
    }

    public StackTraceElement[] getErrorCauseStackTrace() {
        return errorCauseStackTrace;
    }

    public void setErrorCauseStackTrace(StackTraceElement[] errorCauseStackTrace) {
        this.errorCauseStackTrace = errorCauseStackTrace;
    }


}
