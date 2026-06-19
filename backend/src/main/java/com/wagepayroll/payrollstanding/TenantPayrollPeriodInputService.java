package com.wagepayroll.payrollstanding;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.wagepayroll.api.dto.TenantMaterializePayrollInputsResultDto;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionCreateRequest;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionPatchRequest;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionPutRequest;
import com.wagepayroll.api.dto.TenantPayrollStandingInstructionRowDto;
import com.wagepayroll.api.dto.TenantWageComponentTransactionPutRequest;
import com.wagepayroll.api.dto.TenantWageComponentTransactionRowDto;
import com.wagepayroll.audit.AuditActionCodes;
import com.wagepayroll.audit.AuditResourceTypes;
import com.wagepayroll.audit.AuditService;
import com.wagepayroll.domain.org.TenantEmployeeEntity;
import com.wagepayroll.domain.org.TenantEmployeeRepository;
import com.wagepayroll.domain.org.TenantPayPeriodEntity;
import com.wagepayroll.domain.org.TenantPayPeriodRepository;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionEntity;
import com.wagepayroll.domain.payrollstanding.TenantEmployeePayrollStandingInstructionRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentRepository;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionEntity;
import com.wagepayroll.domain.wagecomponent.TenantWageComponentTransactionRepository;
import com.wagepayroll.payroll.country.SurinamePlatformStatutoryComponentMapping;
import com.wagepayroll.payroll.engine.EvaluatedComponentAmount;
import com.wagepayroll.payroll.engine.EvaluatedComponentSource;
import com.wagepayroll.payroll.model.CalculationMethod;
import com.wagepayroll.payroll.model.StandingInstructionRecurrence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantPayrollPeriodInputService {

	private static final int MAX_PAGE_SIZE = 100;
	private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

	private final TenantEmployeePayrollStandingInstructionRepository standingRepository;
	private final TenantWageComponentTransactionRepository transactionRepository;
	private final TenantWageComponentRepository wageComponentRepository;
	private final TenantEmployeeRepository employeeRepository;
	private final TenantPayPeriodRepository payPeriodRepository;
	private final AuditService auditService;

	public TenantPayrollPeriodInputService(TenantEmployeePayrollStandingInstructionRepository standingRepository,
			TenantWageComponentTransactionRepository transactionRepository,
			TenantWageComponentRepository wageComponentRepository, TenantEmployeeRepository employeeRepository,
			TenantPayPeriodRepository payPeriodRepository, AuditService auditService) {
		this.standingRepository = standingRepository;
		this.transactionRepository = transactionRepository;
		this.wageComponentRepository = wageComponentRepository;
		this.employeeRepository = employeeRepository;
		this.payPeriodRepository = payPeriodRepository;
		this.auditService = auditService;
	}

	// ─── Standing instructions ────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public Map<String, Object> listStandingInstructions(UUID tenantId, UUID companyId, UUID employeeId) {
		requireEmployee(tenantId, companyId, employeeId);
		List<TenantEmployeePayrollStandingInstructionEntity> rows = standingRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdOrderByWageComponentProcessingOrderAsc(tenantId, companyId,
						employeeId);
		List<TenantPayrollStandingInstructionRowDto> items = rows.stream().map(this::toStandingRow).toList();
		return Map.of("data", items);
	}

	@Transactional(readOnly = true)
	public TenantPayrollStandingInstructionRowDto getStandingInstruction(UUID tenantId, UUID id) {
		TenantEmployeePayrollStandingInstructionEntity e = standingRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return toStandingRow(e);
	}

	@Transactional
	public TenantPayrollStandingInstructionRowDto createStandingInstruction(UUID tenantId,
			TenantPayrollStandingInstructionCreateRequest body, UUID actorUserId, String correlationId) {
		if (body == null || body.companyId() == null || body.employeeId() == null || body.tenantWageComponentId() == null
				|| body.effectiveFrom() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantEmployeeEntity employee = requireEmployee(tenantId, body.companyId(), body.employeeId());
		TenantWageComponentEntity component = requireWageComponentForCompany(tenantId, body.companyId(),
				body.tenantWageComponentId());
		if (!component.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INACTIVE_WAGE_COMPONENT");
		}
		validateEffectiveRange(body.effectiveFrom(), body.effectiveTo());
		boolean amountOverride = body.amountOverride() != null && body.amountOverride().booleanValue();
		boolean factorOverride = body.factorOverride() != null && body.factorOverride().booleanValue();
		validateAmountModel(body.amount(), body.quantity(), body.rate(), amountOverride, factorOverride);
		StandingInstructionRecurrence recurrence = body.recurrence() != null && !body.recurrence().isBlank()
				? StandingInstructionRecurrence.fromStored(body.recurrence())
				: StandingInstructionRecurrence.EACH_PAY_PERIOD;
		if (recurrence != StandingInstructionRecurrence.EACH_PAY_PERIOD) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_RECURRENCE");
		}
		assertNoOverlappingActiveInstruction(tenantId, body.companyId(), body.employeeId(),
				body.tenantWageComponentId(), body.effectiveFrom(), body.effectiveTo(), null);

		Instant now = Instant.now();
		TenantEmployeePayrollStandingInstructionEntity e = new TenantEmployeePayrollStandingInstructionEntity();
		e.setId(UUID.randomUUID());
		e.setTenantId(tenantId);
		e.setCompanyId(body.companyId());
		e.setEmployeeId(employee.getId());
		e.setTenantWageComponentId(body.tenantWageComponentId());
		e.setEffectiveFrom(body.effectiveFrom());
		e.setEffectiveTo(body.effectiveTo());
		e.setAmount(scaleMoney(body.amount()));
		e.setQuantity(scaleMoney(body.quantity()));
		e.setRate(scaleMoney(body.rate()));
		e.setRecurrence(recurrence);
		e.setActive(true);
		e.setAmountOverride(amountOverride);
		e.setFactorOverride(factorOverride);
		e.setRemarks(trimToNull(body.remarks()));
		e.setCreatedAt(now);
		e.setUpdatedAt(now);
		standingRepository.save(e);

		auditService.append(tenantId, actorUserId, AuditActionCodes.EMPLOYEE_PAYROLL_STANDING_INSTRUCTION_CREATED,
				AuditResourceTypes.TENANT_EMPLOYEE_PAYROLL_STANDING_INSTRUCTION, e.getId().toString(), correlationId,
				Map.of("companyId", body.companyId().toString(), "employeeId", body.employeeId().toString()));

		return toStandingRow(e);
	}

	@Transactional
	public TenantPayrollStandingInstructionRowDto putStandingInstruction(UUID tenantId, UUID id,
			TenantPayrollStandingInstructionPutRequest body, UUID actorUserId, String correlationId) {
		if (body == null || body.companyId() == null || body.employeeId() == null || body.tenantWageComponentId() == null
				|| body.effectiveFrom() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantEmployeePayrollStandingInstructionEntity e = standingRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!e.getCompanyId().equals(body.companyId()) || !e.getEmployeeId().equals(body.employeeId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "COMPANY_OR_EMPLOYEE_MISMATCH");
		}
		requireEmployee(tenantId, body.companyId(), body.employeeId());
		TenantWageComponentEntity component = requireWageComponentForCompany(tenantId, body.companyId(),
				body.tenantWageComponentId());
		if (!component.isActive()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INACTIVE_WAGE_COMPONENT");
		}
		validateEffectiveRange(body.effectiveFrom(), body.effectiveTo());
		boolean amountOverride = body.amountOverride() != null ? body.amountOverride().booleanValue() : e.isAmountOverride();
		boolean factorOverride = body.factorOverride() != null ? body.factorOverride().booleanValue() : e.isFactorOverride();
		validateAmountModel(body.amount(), body.quantity(), body.rate(), amountOverride, factorOverride);
		boolean active = body.active() != null ? body.active().booleanValue() : e.isActive();
		StandingInstructionRecurrence recurrence = body.recurrence() != null && !body.recurrence().isBlank()
				? StandingInstructionRecurrence.fromStored(body.recurrence())
				: StandingInstructionRecurrence.EACH_PAY_PERIOD;
		if (recurrence != StandingInstructionRecurrence.EACH_PAY_PERIOD) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_RECURRENCE");
		}
		assertNoOverlappingActiveInstruction(tenantId, body.companyId(), body.employeeId(),
				body.tenantWageComponentId(), body.effectiveFrom(), body.effectiveTo(), id);

		e.setTenantWageComponentId(body.tenantWageComponentId());
		e.setEffectiveFrom(body.effectiveFrom());
		e.setEffectiveTo(body.effectiveTo());
		e.setAmount(scaleMoney(body.amount()));
		e.setQuantity(scaleMoney(body.quantity()));
		e.setRate(scaleMoney(body.rate()));
		e.setRecurrence(recurrence);
		e.setActive(active);
		e.setAmountOverride(amountOverride);
		e.setFactorOverride(factorOverride);
		e.setRemarks(trimToNull(body.remarks()));
		e.setUpdatedAt(Instant.now());
		standingRepository.save(e);

		auditService.append(tenantId, actorUserId, AuditActionCodes.EMPLOYEE_PAYROLL_STANDING_INSTRUCTION_UPDATED,
				AuditResourceTypes.TENANT_EMPLOYEE_PAYROLL_STANDING_INSTRUCTION, e.getId().toString(), correlationId,
				Map.of("companyId", body.companyId().toString(), "employeeId", body.employeeId().toString()));

		return toStandingRow(e);
	}

	@Transactional
	public TenantPayrollStandingInstructionRowDto patchStandingInstruction(UUID tenantId, UUID id,
			TenantPayrollStandingInstructionPatchRequest body, UUID actorUserId, String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantEmployeePayrollStandingInstructionEntity e = standingRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (body.effectiveTo() != null && body.effectiveTo().isBefore(e.getEffectiveFrom())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EFFECTIVE_TO_BEFORE_FROM");
		}
		if (body.effectiveTo() != null) {
			e.setEffectiveTo(body.effectiveTo());
		}
		if (body.active() != null) {
			e.setActive(body.active().booleanValue());
		}
		if (body.remarks() != null) {
			e.setRemarks(trimToNull(body.remarks()));
		}
		validateEffectiveRange(e.getEffectiveFrom(), e.getEffectiveTo());
		if (e.isActive()) {
			assertNoOverlappingActiveInstruction(tenantId, e.getCompanyId(), e.getEmployeeId(),
					e.getTenantWageComponentId(), e.getEffectiveFrom(), e.getEffectiveTo(), e.getId());
		}
		e.setUpdatedAt(Instant.now());
		standingRepository.save(e);

		auditService.append(tenantId, actorUserId, AuditActionCodes.EMPLOYEE_PAYROLL_STANDING_INSTRUCTION_UPDATED,
				AuditResourceTypes.TENANT_EMPLOYEE_PAYROLL_STANDING_INSTRUCTION, e.getId().toString(), correlationId,
				Map.of("patch", true));

		return toStandingRow(e);
	}

	// ─── Period transactions ──────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public Map<String, Object> listPeriodTransactions(UUID tenantId, UUID companyId, UUID payPeriodId, UUID employeeId,
			int page, int size) {
		requirePayPeriodForCompany(tenantId, companyId, payPeriodId);
		int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
		int safePage = Math.max(page, 0);
		var pr = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("createdAt")));
		Page<TenantWageComponentTransactionEntity> p = employeeId == null
				? transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodId(tenantId, companyId, payPeriodId, pr)
				: transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeId(tenantId, companyId,
						payPeriodId, employeeId, pr);
		List<TenantWageComponentTransactionRowDto> items = p.getContent().stream().map(this::toTransactionRow).toList();
		Map<String, Object> out = new HashMap<>();
		out.put("items", items);
		out.put("totalElements", p.getTotalElements());
		out.put("page", p.getNumber());
		out.put("size", p.getSize());
		out.put("totalPages", p.getTotalPages());
		return out;
	}

	@Transactional
	public TenantWageComponentTransactionRowDto putPeriodTransaction(UUID tenantId, UUID id,
			TenantWageComponentTransactionPutRequest body, UUID actorUserId, String correlationId) {
		if (body == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_BODY");
		}
		TenantWageComponentTransactionEntity e = transactionRepository.findByIdAndTenantId(id, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		boolean monetaryTouch = body.amount() != null || body.quantity() != null || body.rate() != null;
		if (monetaryTouch) {
			boolean explicitAmount = body.amount() != null && body.quantity() == null && body.rate() == null;
			boolean explicitQtyRate = body.quantity() != null && body.rate() != null;
			if (explicitAmount == explicitQtyRate) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AMOUNT_OR_QUANTITY_RATE_REQUIRED");
			}
			if (explicitQtyRate) {
				e.setQuantity(scaleMoney(body.quantity()));
				e.setRate(scaleMoney(body.rate()));
				e.setAmount(scaleMoney(body.quantity().multiply(body.rate())));
			}
			else {
				e.setQuantity(null);
				e.setRate(null);
				e.setAmount(scaleMoney(body.amount()));
			}
		}
		if (body.manualOverride() != null) {
			e.setManualOverride(body.manualOverride().booleanValue());
		}
		if (body.remarks() != null) {
			e.setRemarks(trimToNull(body.remarks()));
		}
		e.setUpdatedAt(Instant.now());
		transactionRepository.save(e);

		auditService.append(tenantId, actorUserId, AuditActionCodes.TENANT_WAGE_COMPONENT_TRANSACTION_UPDATED,
				AuditResourceTypes.TENANT_WAGE_COMPONENT_TRANSACTION, e.getId().toString(), correlationId,
				Map.of("payPeriodId", e.getPayPeriodId().toString(), "employeeId", e.getEmployeeId().toString()));

		return toTransactionRow(e);
	}

	// ─── Materialization ──────────────────────────────────────────────────────

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TenantMaterializePayrollInputsResultDto materializeForPayPeriod(UUID tenantId, UUID companyId,
			UUID payPeriodId, UUID actorUserId, String correlationId) {
		return materializeForPayPeriod(tenantId, companyId, payPeriodId, null, actorUserId, correlationId);
	}

	/**
	 * Materializes standing instructions into period transactions. When {@code employeeIds} is non-null and
	 * non-empty, only those employees are processed (used by formula preview and payroll engine runs).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TenantMaterializePayrollInputsResultDto materializeForPayPeriod(UUID tenantId, UUID companyId,
			UUID payPeriodId, List<UUID> employeeIds, UUID actorUserId, String correlationId) {
		TenantPayPeriodEntity period = requirePayPeriodForCompany(tenantId, companyId, payPeriodId);
		boolean scopedEmployees = employeeIds != null && !employeeIds.isEmpty();
		List<TenantEmployeePayrollStandingInstructionEntity> all = scopedEmployees
				? standingRepository.findByTenantIdAndCompanyIdAndEmployeeIdIn(tenantId, companyId, employeeIds)
				: standingRepository.findByTenantIdAndCompanyId(tenantId, companyId);
		Set<UUID> scopedEmployeeSet = scopedEmployees ? Set.copyOf(employeeIds) : Set.of();
		Map<UUID, TenantEmployeeEntity> employeeById = preloadEmployees(tenantId, companyId, all, scopedEmployeeSet);
		Map<UUID, TenantWageComponentEntity> componentById = preloadWageComponents(tenantId, companyId, all);
		Map<String, TenantWageComponentTransactionEntity> existingTxByKey = preloadPeriodTransactions(tenantId, companyId,
				payPeriodId, scopedEmployees, scopedEmployeeSet);
		int created = 0;
		int updated = 0;
		int skippedManualOverride = 0;
		int skippedInactiveEmployee = 0;
		int skippedInactiveInstruction = 0;
		int skippedInactiveWageComponent = 0;

		for (TenantEmployeePayrollStandingInstructionEntity si : all) {
			if (!si.isActive()) {
				skippedInactiveInstruction++;
				continue;
			}
			if (!rangesOverlap(si.getEffectiveFrom(), si.getEffectiveTo(), period.getStartDate(),
					period.getEndDate())) {
				continue;
			}
			TenantEmployeeEntity employee = employeeById.get(si.getEmployeeId());
			if (employee == null) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND");
			}
			if (!employee.isActive()) {
				skippedInactiveEmployee++;
				continue;
			}
			if (employee.getHireDate().isAfter(period.getEndDate())) {
				skippedInactiveEmployee++;
				continue;
			}
			TenantWageComponentEntity component = componentById.get(si.getTenantWageComponentId());
			if (component == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "WAGE_COMPONENT_NOT_FOUND");
			}
			if (!component.isActive()) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "INACTIVE_WAGE_COMPONENT_MATERIALIZATION");
			}

			var valuesOpt = resolveMaterializedValues(si, component);
			if (valuesOpt.isEmpty()) {
				continue;
			}
			MaterializedTransactionValues values = valuesOpt.get();
			BigDecimal qty = values.quantity();
			BigDecimal rate = values.rate();
			BigDecimal computedAmount = values.amount();

			TenantWageComponentTransactionEntity existing = existingTxByKey
					.get(materializeTransactionKey(si.getEmployeeId(), si.getTenantWageComponentId()));
			if (existing != null) {
				if (existing.isManualOverride()) {
					skippedManualOverride++;
					continue;
				}
				boolean changed = existing.getAmount().compareTo(computedAmount) != 0
						|| !nullableEquals(existing.getQuantity(), qty) || !nullableEquals(existing.getRate(), rate);
				existing.setQuantity(qty);
				existing.setRate(rate);
				existing.setAmount(computedAmount);
				existing.setUpdatedAt(Instant.now());
				transactionRepository.save(existing);
				if (changed) {
					updated++;
				}
				continue;
			}

			TenantWageComponentTransactionEntity tx = new TenantWageComponentTransactionEntity();
			tx.setId(UUID.randomUUID());
			tx.setTenantId(tenantId);
			tx.setCompanyId(companyId);
			tx.setEmployeeId(si.getEmployeeId());
			tx.setPayPeriodId(payPeriodId);
			tx.setPayPeriodRunId(null);
			tx.setTenantWageComponentId(si.getTenantWageComponentId());
			tx.setQuantity(qty);
			tx.setRate(rate);
			tx.setAmount(computedAmount);
			tx.setManualOverride(false);
			tx.setRemarks(null);
			Instant now = Instant.now();
			tx.setCreatedAt(now);
			tx.setUpdatedAt(now);
			transactionRepository.save(tx);
			existingTxByKey.put(materializeTransactionKey(si.getEmployeeId(), si.getTenantWageComponentId()), tx);
			created++;
		}

		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("payPeriodId", payPeriodId.toString());
		meta.put("created", created);
		meta.put("updated", updated);
		meta.put("skippedManualOverride", skippedManualOverride);
		auditService.append(tenantId, actorUserId, AuditActionCodes.PAYROLL_PERIOD_INPUTS_MATERIALIZED,
				AuditResourceTypes.TENANT_WAGE_COMPONENT_TRANSACTION, payPeriodId.toString(), correlationId, meta);

		return new TenantMaterializePayrollInputsResultDto(created, updated, skippedManualOverride,
				skippedInactiveEmployee, skippedInactiveInstruction, skippedInactiveWageComponent);
	}

	/**
	 * Writes formula-preview evaluated amounts into {@code tenant_wage_component_transaction} so employee payroll
	 * inputs show persisted factor/amount on reload. Skips rows with {@code manual_override}. Preserves quantity/rate.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void applyFormulaPreviewToPeriodTransactions(UUID tenantId, UUID companyId, UUID payPeriodId,
			List<EvaluatedComponentAmount> evaluated, UUID actorUserId, String correlationId) {
		if (evaluated == null || evaluated.isEmpty()) {
			return;
		}
		requirePayPeriodForCompany(tenantId, companyId, payPeriodId);
		Set<UUID> employeeIds = evaluated.stream().map(EvaluatedComponentAmount::employeeId).collect(Collectors.toSet());
		Map<String, TenantWageComponentTransactionEntity> existingTxByKey = preloadPeriodTransactions(tenantId, companyId,
				payPeriodId, true, employeeIds);
		Map<String, TenantWageComponentEntity> tenantComponentByCode = wageComponentRepository
				.findByTenantIdAndCompanyIdAndActiveIsTrueOrderByProcessingOrderAsc(tenantId, companyId)
				.stream()
				.collect(Collectors.toMap(TenantWageComponentEntity::getCode, Function.identity(), (a, b) -> a));
		int updated = 0;
		int created = 0;
		Instant now = Instant.now();
		for (EvaluatedComponentAmount ev : evaluated) {
			if (ev.evaluatedAmount() == null) {
				continue;
			}
			UUID tenantComponentId = ev.tenantWageComponentId();
			if (tenantComponentId == null && ev.componentSource() == EvaluatedComponentSource.PLATFORM) {
				tenantComponentId = SurinamePlatformStatutoryComponentMapping.resolveTenantWageComponentId(
						ev.tenantWageComponentCode(), tenantComponentByCode);
			}
			if (tenantComponentId == null) {
				continue;
			}
			TenantWageComponentTransactionEntity existing = resolveExistingPeriodTransaction(tenantId, payPeriodId,
					ev.employeeId(), tenantComponentId, existingTxByKey);
			if (existing != null && existing.isManualOverride()) {
				continue;
			}
			BigDecimal amount = scaleMoney(ev.evaluatedAmount());
			if (existing != null) {
				if (existing.getAmount().compareTo(amount) != 0) {
					existing.setAmount(amount);
					existing.setUpdatedAt(now);
					transactionRepository.save(existing);
					updated++;
				}
				continue;
			}
			TenantWageComponentTransactionEntity tx = new TenantWageComponentTransactionEntity();
			tx.setId(UUID.randomUUID());
			tx.setTenantId(tenantId);
			tx.setCompanyId(companyId);
			tx.setEmployeeId(ev.employeeId());
			tx.setPayPeriodId(payPeriodId);
			tx.setPayPeriodRunId(null);
			tx.setTenantWageComponentId(tenantComponentId);
			tx.setQuantity(null);
			tx.setRate(null);
			tx.setAmount(amount);
			tx.setManualOverride(false);
			tx.setRemarks(null);
			tx.setCreatedAt(now);
			tx.setUpdatedAt(now);
			transactionRepository.save(tx);
			existingTxByKey.put(materializeTransactionKey(ev.employeeId(), tenantComponentId), tx);
			created++;
		}
		Map<String, Object> meta = new LinkedHashMap<>();
		meta.put("payPeriodId", payPeriodId.toString());
		meta.put("updated", updated);
		meta.put("created", created);
		auditService.append(tenantId, actorUserId, AuditActionCodes.TENANT_WAGE_COMPONENT_TRANSACTION_UPDATED,
				AuditResourceTypes.TENANT_WAGE_COMPONENT_TRANSACTION, payPeriodId.toString(), correlationId, meta);
	}

	// ─── Helpers ───────────────────────────────────────────────────────────────

	private Map<UUID, TenantEmployeeEntity> preloadEmployees(UUID tenantId, UUID companyId,
			List<TenantEmployeePayrollStandingInstructionEntity> standings, Set<UUID> scopedEmployeeIds) {
		Set<UUID> ids = standings.stream().map(TenantEmployeePayrollStandingInstructionEntity::getEmployeeId)
				.collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return Map.of();
		}
		List<TenantEmployeeEntity> rows = scopedEmployeeIds.isEmpty()
				? employeeRepository.findByTenantIdAndCompanyIdAndIdIn(tenantId, companyId, ids)
				: employeeRepository.findByTenantIdAndCompanyIdAndIdIn(tenantId, companyId, scopedEmployeeIds);
		return rows.stream().collect(Collectors.toMap(TenantEmployeeEntity::getId, Function.identity()));
	}

	private Map<UUID, TenantWageComponentEntity> preloadWageComponents(UUID tenantId, UUID companyId,
			List<TenantEmployeePayrollStandingInstructionEntity> standings) {
		Set<UUID> ids = standings.stream().map(TenantEmployeePayrollStandingInstructionEntity::getTenantWageComponentId)
				.collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return Map.of();
		}
		return wageComponentRepository.findByTenantIdAndCompanyIdAndIdIn(tenantId, companyId, ids).stream()
				.collect(Collectors.toMap(TenantWageComponentEntity::getId, Function.identity()));
	}

	private Map<String, TenantWageComponentTransactionEntity> preloadPeriodTransactions(UUID tenantId, UUID companyId,
			UUID payPeriodId, boolean scopedEmployees, Set<UUID> scopedEmployeeIds) {
		List<TenantWageComponentTransactionEntity> rows = scopedEmployees
				? transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodIdAndEmployeeIdIn(tenantId, companyId,
						payPeriodId, scopedEmployeeIds)
				: transactionRepository.findByTenantIdAndCompanyIdAndPayPeriodId(tenantId, companyId, payPeriodId);
		Map<String, TenantWageComponentTransactionEntity> byKey = new HashMap<>();
		for (TenantWageComponentTransactionEntity row : rows) {
			byKey.put(materializeTransactionKey(row.getEmployeeId(), row.getTenantWageComponentId()), row);
		}
		return byKey;
	}

	private static String materializeTransactionKey(UUID employeeId, UUID componentId) {
		return employeeId + ":" + componentId;
	}

	private TenantWageComponentTransactionEntity resolveExistingPeriodTransaction(UUID tenantId, UUID payPeriodId,
			UUID employeeId, UUID tenantWageComponentId,
			Map<String, TenantWageComponentTransactionEntity> cache) {
		String key = materializeTransactionKey(employeeId, tenantWageComponentId);
		TenantWageComponentTransactionEntity cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		return transactionRepository
				.findByTenantIdAndPayPeriodIdAndEmployeeIdAndTenantWageComponentId(tenantId, payPeriodId, employeeId,
						tenantWageComponentId)
				.map(row -> {
					cache.put(key, row);
					return row;
				}).orElse(null);
	}

	private TenantEmployeeEntity requireEmployee(UUID tenantId, UUID companyId, UUID employeeId) {
		return employeeRepository.findByIdAndTenantIdAndCompanyId(employeeId, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND"));
	}

	private TenantPayPeriodEntity requirePayPeriodForCompany(UUID tenantId, UUID companyId, UUID payPeriodId) {
		TenantPayPeriodEntity p = payPeriodRepository.findByIdAndTenantId(payPeriodId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		if (!p.getCompanyId().equals(companyId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PAY_PERIOD_COMPANY_MISMATCH");
		}
		return p;
	}

	private TenantWageComponentEntity requireWageComponentForCompany(UUID tenantId, UUID companyId, UUID componentId) {
		return wageComponentRepository.findByIdAndTenantIdAndCompanyId(componentId, tenantId, companyId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "WAGE_COMPONENT_COMPANY_MISMATCH"));
	}

	private TenantPayrollStandingInstructionRowDto toStandingRow(TenantEmployeePayrollStandingInstructionEntity e) {
		TenantWageComponentEntity wc = wageComponentRepository.findByIdAndTenantId(e.getTenantWageComponentId(), e.getTenantId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new TenantPayrollStandingInstructionRowDto(e.getId(), e.getCompanyId(), e.getEmployeeId(),
				e.getTenantWageComponentId(), wc.getCode(), wc.getName(), e.getEffectiveFrom(), e.getEffectiveTo(),
				e.getAmount(), e.getQuantity(), e.getRate(), e.getRecurrence().name(), e.isActive(),
				e.isAmountOverride(), e.isFactorOverride(), e.getRemarks(), e.getCreatedAt(), e.getUpdatedAt());
	}

	private TenantWageComponentTransactionRowDto toTransactionRow(TenantWageComponentTransactionEntity e) {
		TenantWageComponentEntity wc = wageComponentRepository.findByIdAndTenantId(e.getTenantWageComponentId(), e.getTenantId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new TenantWageComponentTransactionRowDto(e.getId(), e.getCompanyId(), e.getEmployeeId(),
				e.getPayPeriodId(), e.getPayPeriodRunId(), e.getTenantWageComponentId(), wc.getCode(), wc.getName(),
				e.getQuantity(), e.getRate(), e.getAmount(), e.isManualOverride(), e.getRemarks(), e.getCreatedAt(),
				e.getUpdatedAt());
	}

	private void validateEffectiveRange(LocalDate from, LocalDate to) {
		if (to != null && to.isBefore(from)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EFFECTIVE_TO_BEFORE_FROM");
		}
	}

	private void validateAmountModel(BigDecimal amount, BigDecimal quantity, BigDecimal rate, boolean amountOverride,
			boolean factorOverride) {
		if (rate != null && quantity == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QUANTITY_REQUIRED_WITH_RATE");
		}
		if (rate != null && amount != null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AMOUNT_RATE_MUTUALLY_EXCLUSIVE");
		}
		if (amountOverride && amount == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AMOUNT_REQUIRED_WHEN_OVERRIDE");
		}
		if (factorOverride && quantity == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "FACTOR_REQUIRED_WHEN_OVERRIDE");
		}
		if (quantity != null && rate != null && (quantity.signum() == 0 || rate.signum() == 0)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY_OR_RATE");
		}
	}

	private void assertNoOverlappingActiveInstruction(UUID tenantId, UUID companyId, UUID employeeId,
			UUID tenantWageComponentId, LocalDate effectiveFrom, LocalDate effectiveTo, UUID excludeId) {
		List<TenantEmployeePayrollStandingInstructionEntity> same = standingRepository
				.findByTenantIdAndCompanyIdAndEmployeeIdAndTenantWageComponentId(tenantId, companyId, employeeId,
						tenantWageComponentId);
		for (TenantEmployeePayrollStandingInstructionEntity other : same) {
			if (excludeId != null && other.getId().equals(excludeId)) {
				continue;
			}
			if (!other.isActive()) {
				continue;
			}
			if (rangesOverlap(effectiveFrom, effectiveTo, other.getEffectiveFrom(), other.getEffectiveTo())) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "STANDING_INSTRUCTION_OVERLAP");
			}
		}
	}

	private static boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
		LocalDate aEnd = aTo != null ? aTo : OPEN_END;
		LocalDate bEnd = bTo != null ? bTo : OPEN_END;
		return !aFrom.isAfter(bEnd) && !bFrom.isAfter(aEnd);
	}

	private static java.util.Optional<MaterializedTransactionValues> resolveMaterializedValues(
			TenantEmployeePayrollStandingInstructionEntity si, TenantWageComponentEntity component) {
		BigDecimal qty = scaleMoney(si.getQuantity());
		BigDecimal rate = scaleMoney(si.getRate());

		if (component.getCalculationMethod() == CalculationMethod.FORMULA) {
			if (si.isAmountOverride()) {
				BigDecimal amt = scaleMoney(si.getAmount());
				if (amt == null) {
					return java.util.Optional.empty();
				}
				return java.util.Optional.of(new MaterializedTransactionValues(qty, rate, amt));
			}
			if (si.isFactorOverride() && qty != null) {
				return java.util.Optional
						.of(new MaterializedTransactionValues(qty, rate, scaleMoney(BigDecimal.ZERO)));
			}
			BigDecimal legacyAmt = scaleMoney(si.getAmount());
			if (legacyAmt != null && qty == null) {
				return java.util.Optional.of(new MaterializedTransactionValues(null, null, legacyAmt));
			}
			if (qty != null && qty.signum() > 0) {
				return java.util.Optional
						.of(new MaterializedTransactionValues(qty, rate, scaleMoney(BigDecimal.ZERO)));
			}
			return java.util.Optional.empty();
		}

		BigDecimal amtForCompute = si.isAmountOverride() ? scaleMoney(si.getAmount()) : null;
		BigDecimal computedAmount = computedPeriodAmount(amtForCompute, qty, rate);
		if (computedAmount != null) {
			return java.util.Optional.of(new MaterializedTransactionValues(qty, rate, computedAmount));
		}
		if (qty != null && qty.signum() > 0) {
			return java.util.Optional
					.of(new MaterializedTransactionValues(qty, rate, scaleMoney(BigDecimal.ZERO)));
		}
		return java.util.Optional.empty();
	}

	private record MaterializedTransactionValues(BigDecimal quantity, BigDecimal rate, BigDecimal amount) {
	}

	private static BigDecimal computedPeriodAmount(BigDecimal amount, BigDecimal quantity, BigDecimal rate) {
		if (amount != null) {
			return scaleMoney(amount);
		}
		if (quantity != null && rate != null) {
			return scaleMoney(quantity.multiply(rate));
		}
		return null;
	}

	private static BigDecimal scaleMoney(BigDecimal v) {
		if (v == null) {
			return null;
		}
		return v.setScale(4, RoundingMode.HALF_UP);
	}

	private static String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private static boolean nullableEquals(BigDecimal a, BigDecimal b) {
		if (a == null && b == null) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		return a.compareTo(b) == 0;
	}
}
