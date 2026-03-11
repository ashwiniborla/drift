/**
 * order_delivery_use_case_2111.groovy
 *
 * IMSv3 port of OrderDeliveryCodeBaseWorkflowExecutionV2.runWorkflow() for
 * issueId 2111.  Evaluates order-unit statuses from the Oxford response and
 * determines whether the E2E use case has reached a terminal state.
 *
 * Email / event-generation logic is intentionally excluded.
 *
 * Context:
 *   _global.e2e_fetch_order_details.units         – full units map from Oxford
 *   _global.e2e_fetch_order_details.targetUnitIds  – list of unit IDs to check
 *
 * Returns:
 *   [ useCaseName: "<name>", ended: true|false ]
 */

def DELIVERED_STATUSES = ['delivered', 'completed'] as Set

// ── helper closures ────────────────────────────────────────────────────

def isUnitDelivered = { unit ->
    def histories = unit?.statusHistories ?: []
    return histories.any { h ->
        DELIVERED_STATUSES.contains(h?.status?.toString()?.toLowerCase())
    }
}

def isChoreTypeReturnOrCancel = { chore ->
    def type = chore?.choreType?.toString()?.toUpperCase()
    return type == 'RETURN' || type == 'CANCEL'
}

def isChoreStatusActiveOrCompleted = { chore ->
    def status = chore?.status?.toString()?.toUpperCase()
    return status == 'ACTIVE' || status == 'COMPLETED'
}

def isUnitCancelledOrCourierReturn = { unit ->
    def chores = unit?.chores ?: []
    if (chores.isEmpty()) return false
    return chores.any { chore ->
        if (!isChoreTypeReturnOrCancel(chore)) return false
        if (!isChoreStatusActiveOrCompleted(chore)) return false
        if (chore?.choreType?.toString()?.toUpperCase() == 'RETURN') {
            return chore?.meta?.returnType?.toString()?.toUpperCase() == 'COURIER_RETURN'
        }
        return true
    }
}

def isUnitRejected = { unit ->
    return unit?.status?.toString()?.toUpperCase() == 'REJECTED'
}

def isCpdBreached = { unit ->
    def promiseBag = unit?.promiseDataBag
    if (!promiseBag) return false
    long updatedTo = promiseBag.updatedPromisedDateTo ?: 0L
    long originalTo = promiseBag.promisedDateTo ?: 0L
    long promiseEnd = (updatedTo != 0) ? updatedTo : originalTo
    if (promiseEnd == 0) return false
    return System.currentTimeMillis() > promiseEnd
}

// ── load data from previous node ───────────────────────────────────────

def fetchResult = _global?.e2e_fetch_order_details
if (!fetchResult) {
    throw new Exception("e2e_fetch_order_details output not found in workflow context")
}

def allUnits = fetchResult.units ?: [:]
def targetUnitIds = fetchResult.targetUnitIds ?: []
if (targetUnitIds.isEmpty()) {
    throw new Exception("No target unit IDs found in workflow context")
}

def targetUnits = targetUnitIds.collect { uid -> allUnits[uid] }.findAll { it != null }
if (targetUnits.isEmpty()) {
    throw new Exception("None of the target unit IDs matched units in Oxford response")
}

// ── decision tree (mirrors OrderDeliveryCodeBaseWorkflowExecutionV2.runWorkflow) ──

// 1. All items rejected
if (targetUnits.every { isUnitRejected(it) }) {
    return [useCaseName: 'ALL_ITEMS_REJECTED', ended: true]
}

// 2. Any item CPD breached (only for non-delivered, non-cancelled units)
def activeUnits = targetUnits.findAll { unit ->
    !isUnitDelivered(unit) && !isUnitCancelledOrCourierReturn(unit)
}
if (activeUnits.any { isCpdBreached(it) }) {
    return [useCaseName: 'CPD_BREACHED', ended: true]
}

// 3. All items delivered
if (targetUnits.every { isUnitDelivered(it) }) {
    return [useCaseName: 'ALL_ITEMS_DELIVERED', ended: true]
}

// 4. All items cancelled / courier-returned
if (targetUnits.every { isUnitCancelledOrCourierReturn(it) }) {
    return [useCaseName: 'ALL_ITEMS_CANCELLED', ended: true]
}

// 5. All items post-SLA (each unit is either delivered or cancelled)
if (targetUnits.every { isUnitDelivered(it) || isUnitCancelledOrCourierReturn(it) }) {
    return [useCaseName: 'ALL_ITEMS_POST_SLA', ended: true]
}

// 6. Use case still running
return [useCaseName: 'USE_CASE_RUNNING', ended: false]
