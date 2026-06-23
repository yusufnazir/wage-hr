package com.wagepayroll.org;

import java.util.List;
import java.util.UUID;

import com.wagepayroll.domain.compensation.TenantEmployeeCompensationRepository;
import com.wagepayroll.domain.document.DocumentAttachmentEntity;
import com.wagepayroll.domain.document.DocumentAttachmentRepository;
import com.wagepayroll.domain.employeepayment.TenantEmployeePayPeriodPaymentRepository;
import com.wagepayroll.domain.employeepayment.TenantEmployeePaymentDestinationRepository;
import com.wagepayroll.domain.org.TenantDepartmentEntity;
import com.wagepayroll.domain.org.TenantDepartmentRepository;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.payroll.TenantPayrollYtdAccumulatorRepository;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollLedgerPostingRepository;
import com.wagepayroll.domain.wagecomponent.TenantPayrollResultLineRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentBalanceTransactionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantEmployeeDeletionService {

	private static final String ENTITY_TYPE_EMPLOYEE = "EMPLOYEE";

	private final TenantEmployeeRepository employeeRepository;
	private final TenantDepartmentRepository departmentRepository;
	private final DocumentAttachmentRepository documentAttachmentRepository;
	private final TenantEmployeePaymentDestinationRepository paymentDestinationRepository;
	private final TenantEmployeePayPeriodPaymentRepository payPeriodPaymentRepository;
	private final TenantEmployeePayrollStandingInstructionRepository standingInstructionRepository;
	private final TenantWageComponentTransactionRepository wageComponentTransactionRepository;
	private final TenantPayrollYtdAccumulatorRepository ytdAccumulatorRepository;
	private final TenantWageComponentBalanceRepository wageComponentBalanceRepository;
	private final TenantWageComponentBalanceTransactionRepository wageComponentBalanceTransactionRepository;
	private final TenantEmployeeCompensationRepository compensationRepository;
	private final TenantPayrollResultLineRepository payrollResultLineRepository;
	private final TenantPayrollLedgerPostingRepository payrollLedgerPostingRepository;

	public TenantEmployeeDeletionService(TenantEmployeeRepository employeeRepository,
			TenantDepartmentRepository departmentRepository, DocumentAttachmentRepository documentAttachmentRepository,
			TenantEmployeePaymentDestinationRepository paymentDestinationRepository,
			TenantEmployeePayPeriodPaymentRepository payPeriodPaymentRepository,
			TenantEmployeePayrollStandingInstructionRepository standingInstructionRepository,
			TenantWageComponentTransactionRepository wageComponentTransactionRepository,
			TenantPayrollYtdAccumulatorRepository ytdAccumulatorRepository,
			TenantWageComponentBalanceRepository wageComponentBalanceRepository,
			TenantWageComponentBalanceTransactionRepository wageComponentBalanceTransactionRepository,
			TenantEmployeeCompensationRepository compensationRepository,
			TenantPayrollResultLineRepository payrollResultLineRepository,
			TenantPayrollLedgerPostingRepository payrollLedgerPostingRepository) {
		this.employeeRepository = employeeRepository;
		this.departmentRepository = departmentRepository;
		this.documentAttachmentRepository = documentAttachmentRepository;
		this.paymentDestinationRepository = paymentDestinationRepository;
		this.payPeriodPaymentRepository = payPeriodPaymentRepository;
		this.standingInstructionRepository = standingInstructionRepository;
		this.wageComponentTransactionRepository = wageComponentTransactionRepository;
		this.ytdAccumulatorRepository = ytdAccumulatorRepository;
		this.wageComponentBalanceRepository = wageComponentBalanceRepository;
		this.wageComponentBalanceTransactionRepository = wageComponentBalanceTransactionRepository;
		this.compensationRepository = compensationRepository;
		this.payrollResultLineRepository = payrollResultLineRepository;
		this.payrollLedgerPostingRepository = payrollLedgerPostingRepository;
	}

	@Transactional
	public void deleteEmployee(UUID tenantId, UUID employeeId) {
		TenantEmployeeEntity employee = employeeRepository.findByIdAndTenantId(employeeId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));
		if (!isDraft(employee) && hasPayrollHistory(tenantId, employeeId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"EMPLOYEE_HAS_PAYROLL_HISTORY: deactivate the employee instead of deleting");
		}
		clearDepartmentManagers(tenantId, employeeId);
		deleteDocumentAttachments(tenantId, employeeId);
		paymentDestinationRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		payPeriodPaymentRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		standingInstructionRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		wageComponentTransactionRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		ytdAccumulatorRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		deleteWageComponentBalances(tenantId, employeeId);
		compensationRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
		employeeRepository.delete(employee);
	}

	private static boolean isDraft(TenantEmployeeEntity employee) {
		return employee.getStatus() != null && "DRAFT".equalsIgnoreCase(employee.getStatus().trim());
	}

	private boolean hasPayrollHistory(UUID tenantId, UUID employeeId) {
		return payrollResultLineRepository.existsByTenantIdAndEmployeeId(tenantId, employeeId)
				|| payPeriodPaymentRepository.existsByTenantIdAndEmployeeId(tenantId, employeeId)
				|| payrollLedgerPostingRepository.existsByTenantIdAndEmployeeId(tenantId, employeeId);
	}

	private void clearDepartmentManagers(UUID tenantId, UUID employeeId) {
		List<TenantDepartmentEntity> departments = departmentRepository
				.findByTenantIdAndManagerEmployeeId(tenantId, employeeId);
		if (departments.isEmpty()) {
			return;
		}
		for (TenantDepartmentEntity department : departments) {
			department.setManagerEmployeeId(null);
		}
		departmentRepository.saveAll(departments);
	}

	private void deleteDocumentAttachments(UUID tenantId, UUID employeeId) {
		List<DocumentAttachmentEntity> attachments = documentAttachmentRepository
				.findByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(tenantId, ENTITY_TYPE_EMPLOYEE, employeeId);
		if (!attachments.isEmpty()) {
			documentAttachmentRepository.deleteAll(attachments);
		}
	}

	private void deleteWageComponentBalances(UUID tenantId, UUID employeeId) {
		List<TenantWageComponentBalanceEntity> balances = wageComponentBalanceRepository
				.findByTenantIdAndEmployeeId(tenantId, employeeId);
		for (TenantWageComponentBalanceEntity balance : balances) {
			wageComponentBalanceTransactionRepository.deleteByTenantIdAndBalanceId(tenantId, balance.getId());
		}
		wageComponentBalanceRepository.deleteByTenantIdAndEmployeeId(tenantId, employeeId);
	}
}
