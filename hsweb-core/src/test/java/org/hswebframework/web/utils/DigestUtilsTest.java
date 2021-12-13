package org.hswebframework.web.utils;

import lombok.SneakyThrows;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

public class DigestUtilsTest {


    @Test
    @SneakyThrows
    public void test() {
        Set<String> check = ConcurrentHashMap.newKeySet();

        for (int i = 0; i < 1000; i++) {
            new Thread(() -> check.add(DigestUtils.md5Hex("test")))
                    .start();
        }
        Thread.sleep(1000);
        System.out.println(check);
        assertEquals(1, check.size());
    }
}