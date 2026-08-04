package com.example.demo;

import com.yubraj.test.scanrest.autoconfigure.EnableScanRest;
import com.yubraj.test.scanrest.autoconfigure.ScanRestTestRunner;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Collection;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnableScanRest
class DemoApplicationTests {

	@Autowired
	private ScanRestTestRunner scanRestRunner;

	@TestFactory
	Collection<DynamicTest> apiTests() {
		return scanRestRunner.toDynamicTests();
	}

}
