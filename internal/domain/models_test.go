package domain

import "testing"

func TestBillLifecycleApproveComplete(t *testing.T) {
	bill := Bill{Status: BillStatusPending}

	processing, ok := bill.Approve()
	if !ok {
		t.Fatal("Approve() returned false for pending bill")
	}
	if processing.Status != BillStatusProcessing {
		t.Fatalf("Approve() status = %s", processing.Status)
	}

	completed, ok := processing.Complete()
	if !ok {
		t.Fatal("Complete() returned false for processing bill")
	}
	if completed.Status != BillStatusCompleted {
		t.Fatalf("Complete() status = %s", completed.Status)
	}
}

func TestBillLifecycleRejectsInvalidTransitions(t *testing.T) {
	completed := Bill{Status: BillStatusCompleted}
	if _, ok := completed.Approve(); ok {
		t.Fatal("Approve() succeeded for completed bill")
	}
	if _, ok := completed.Decline(); ok {
		t.Fatal("Decline() succeeded for completed bill")
	}
	if _, ok := completed.MarkAsFailed(); ok {
		t.Fatal("MarkAsFailed() succeeded for completed bill")
	}
}

func TestBillLifecycleDecline(t *testing.T) {
	bill := Bill{Status: BillStatusPending}

	declined, ok := bill.Decline()
	if !ok {
		t.Fatal("Decline() returned false for pending bill")
	}
	if declined.Status != BillStatusRejected {
		t.Fatalf("Decline() status = %s", declined.Status)
	}
}

func TestBalanceChangeNetChange(t *testing.T) {
	data := BalanceChangeData{InAmount: 120, OutAmount: 45}
	if data.NetChange() != 75 {
		t.Fatalf("NetChange() = %d", data.NetChange())
	}
}
