package kz.kayak.bookkeeper.service;

import java.util.NoSuchElementException;

final class EntityLookup {
    private EntityLookup() {}

    static <T> T get(org.springframework.data.jpa.repository.JpaRepository<T, Long> r, long id, String label) {
        return r.findById(id).orElseThrow(() -> new NoSuchElementException(label + " " + id + " was not found"));
    }
}
