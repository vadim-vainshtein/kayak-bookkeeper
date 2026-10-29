package kz.kayak.bookkeeper.service;

public class DomainException extends RuntimeException {
    public DomainException(String message) { super(message); }
}
