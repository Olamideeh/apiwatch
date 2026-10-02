package com.example.apiwatch.exception;

public class InactiveUserException extends RuntimeException {

    public InactiveUserException() {
        super("User account is inactive");
    }
}
