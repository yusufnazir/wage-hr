package com.wagepayroll.api;

import com.wagepayroll.api.dto.EmployeeAccountActivateRequest;
import com.wagepayroll.auth.EmployeeAccountActivationService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/employee-account")
public class EmployeeAccountActivationController {

	private final EmployeeAccountActivationService activationService;

	public EmployeeAccountActivationController(EmployeeAccountActivationService activationService) {
		this.activationService = activationService;
	}

	@PostMapping("/activate")
	public ResponseEntity<Void> activate(@RequestBody EmployeeAccountActivateRequest body) {
		activationService.activate(body);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
