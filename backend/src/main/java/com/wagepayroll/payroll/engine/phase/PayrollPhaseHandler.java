package com.wagepayroll.payroll.engine.phase;

import com.wagepayroll.payroll.engine.PayrollRunPhase;
import com.wagepayroll.payroll.engine.PayrollRunState;

public interface PayrollPhaseHandler {

	PayrollRunPhase phase();

	void execute(PayrollRunState state);
}
