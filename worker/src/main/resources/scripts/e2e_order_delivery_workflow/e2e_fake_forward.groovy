/**
 * e2e_fake_forward.groovy
 *
 * IMSv3 port of OrderDelayCodeBaseWorkflowExecutionV2.runWorkflow() (Delay in Delivery).
 * Evaluates order-unit statuses from the Oxford response and determines whether the
 * E2E use case has reached a terminal state.
 *
 * Key differences from the base OrderDeliveryCodeBaseWorkflowExecutionV2:
 *   - No ALL_ITEMS_REJECTED terminal check
 *   - CPD breach does NOT end the workflow (in DID it only triggers a mail; omitted here)
 *   - Unhappy flow uses cancellation/courier-return logic (no reship EKL service calls)
 *
 * Email / event-generation logic is intentionally excluded.
 *
 * Context:
 *   _global.orderDetails              – list of maps with orderItemUnitId (source of target units)
 *   _global.fetch_order_oxford.units – top-level units map from Oxford (JSON: oms3_order_data.units)
 *
 * Returns:
 *   [ useCaseName: "<name>", ended: true|false ]
 */

// ── status / chore constants (move to enums in future) ─────────────────────

def DELIVERED_STATUSES      = ['delivered', 'completed'] as Set
def CHORE_TERMINAL_STATUSES = ['ACTIVE', 'COMPLETED'] as Set
def CHORE_CANCEL_TYPES      = ['CANCEL', 'RETURN'] as Set
def COURIER_RETURN_TYPE     = 'COURIER_RETURN'

// ── use-case name constants ─────────────────────────────────────────────────

def UC_ALL_DELIVERED = 'ALL_ITEMS_DELIVERED'
def UC_ALL_CANCELLED = 'ALL_ITEMS_CANCELLED'
def UC_ALL_POST_SLA  = 'ALL_ITEMS_POST_SLA'
def UC_RUNNING       = 'USE_CASE_RUNNING'

// ── Oxford unit lookup (keys in units map match unit.id; see drift/oxfordResponse.json) ─

def flattenUnitsRecursive(Map rootUnits) {
    def flat = [:]
    def visit
    visit = { unit, mapKey ->
        if (unit == null) {
            return
        }
        def uid = unit.id?.toString() ?: (mapKey != null ? mapKey.toString() : null)
        if (uid) {
            flat[uid] = unit
        }
        def children = unit.childUnits
        if (children instanceof Map) {
            children.each { k, child -> visit(child, k) }
        } else if (children instanceof List) {
            children.eachWithIndex { child, idx -> visit(child, idx.toString()) }
        }
    }
    if (rootUnits instanceof Map) {
        rootUnits.each { k, unit -> visit(unit, k) }
    }
    return flat
}

/**
 * Resolves a unit by orderItemUnitId: Oxford keys the units map by the same id string as unit.id.
 */
def resolveUnitByOrderItemUnitId(Map topLevelUnits, Map allUnitsFlat, String orderItemUnitId) {
    if (!orderItemUnitId?.trim()) {
        return null
    }
    def id = orderItemUnitId.toString()
    def u = allUnitsFlat[id] ?: topLevelUnits[id]
    if (!u) {
        u = allUnitsFlat?.values()?.find { it?.id?.toString() == id }
    }
    if (!u) {
        u = topLevelUnits?.values()?.find { it?.id?.toString() == id }
    }
    return u
}

// ── helper closures ────────────────────────────────────────────────────────

def isUnitDelivered = { unit ->
    def histories = unit?.statusHistories ?: []
    return histories.any { h ->
        DELIVERED_STATUSES.contains(h?.status?.toString()?.toLowerCase())
    }
}

def isUnitCancelledOrCourierReturn = { unit ->
    def chores = unit?.chores ?: []
    if (chores.isEmpty()) return false
    return chores.any { chore ->
        def choreType   = chore?.choreType?.toString()?.toUpperCase()
        def choreStatus = chore?.status?.toString()?.toUpperCase()
        if (!CHORE_CANCEL_TYPES.contains(choreType)) return false
        if (!CHORE_TERMINAL_STATUSES.contains(choreStatus)) return false
        if (choreType == 'RETURN') {
            return chore?.meta?.returnType?.toString()?.toUpperCase() == COURIER_RETURN_TYPE
        }
        return true
    }
}

// ── load data from previous node ───────────────────────────────────────────

def fetchResult = _global?.fetch_order_oxford
if (!fetchResult) {
    throw new Exception("fetch_order_oxford output not found in workflow context — ensure extract_oxford_order_units ran before this node")
}

def topLevelUnits = fetchResult.units ?: [:]
def allUnitsFlat = fetchResult.allUnitsFlat
if (allUnitsFlat == null && topLevelUnits) {
    allUnitsFlat = flattenUnitsRecursive(topLevelUnits)
}
if (!allUnitsFlat) {
    allUnitsFlat = [:]
}

def orderDetails = _global?.orderDetails ?: []
def targetUnitIds = orderDetails.collect { it?.orderItemUnitId?.toString() }.findAll { it }
if (targetUnitIds.isEmpty()) {
    throw new Exception("No orderItemUnitId entries in _global.orderDetails")
}

def targetUnits = targetUnitIds.collect { uid ->
    def u = resolveUnitByOrderItemUnitId(topLevelUnits, allUnitsFlat, uid)
    if (!u) {
        throw new Exception("No unit found in Oxford data for orderItemUnitId: ${uid}")
    }
    u
}

// ── decision tree (mirrors OrderDelayCodeBaseWorkflowExecutionV2.runWorkflow) ──

// 1. All items delivered (happy flow)
if (targetUnits.every { isUnitDelivered(it) }) {
    return [useCaseName: UC_ALL_DELIVERED, ended: true]
}

// 2. All items cancelled / courier-returned (unhappy flow)
if (targetUnits.every { isUnitCancelledOrCourierReturn(it) }) {
    return [useCaseName: UC_ALL_CANCELLED, ended: true]
}

// 3. All items post-SLA (every unit is either delivered or cancelled)
if (targetUnits.every { isUnitDelivered(it) || isUnitCancelledOrCourierReturn(it) }) {
    return [useCaseName: UC_ALL_POST_SLA, ended: true]
}

// 4. Use case still running
return [useCaseName: UC_RUNNING, ended: false]
