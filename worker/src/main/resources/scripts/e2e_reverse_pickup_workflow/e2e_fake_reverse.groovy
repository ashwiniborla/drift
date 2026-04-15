/**
 * e2e_fake_reverse.groovy
 *
 * IMSv3 port of DelayInPickupWorkflow happy/unhappy evaluation (Delay in Pickup / reverse pickup).
 * Resolves return pickup child units under each target delivery unit from the Oxford response and
 * determines whether the E2E use case has reached a terminal state.
 *
 * Mirrors DelayInPickupWorkflow.isHappyFlow / isUnHappyFlow (unit-status path only; no EKL tracking map).
 *
 * Email / event-generation / breach / CD-delay logic is intentionally excluded (same as e2e_fake_forward).
 *
 * Context:
 *   _global.orderDetails              – list of maps with orderItemUnitId (delivery/parent unit to walk from)
 *   _global.fetch_order_oxford.units  – top-level units map from Oxford (JSON: oms3_order_data.units)
 *   child units use JSON fields: flow, unitSubType, status (see drift/oxfordResponse.json)
 *
 * Returns:
 *   [ useCaseName: "<name>", ended: true|false ]
 */

// ── unit status constants (OrderItemUnitStatus names, lowercased for JSON) ─

def COMPLETED_STATUSES = ['completed', 'picked'] as Set
def CANCELLED_STATUSES = ['cancelled', 'rejected'] as Set

// ── use-case name constants ─────────────────────────────────────────────────

def UC_ALL_PICKUP_COMPLETED = 'ALL_RETURN_UNITS_COMPLETED'
def UC_ALL_CANCELLED        = 'ALL_RETURN_UNITS_CANCELLED'
def UC_RUNNING              = 'USE_CASE_RUNNING'

// ── flatten nested Oxford units (same as extract_oxford_order_units / elixir script) ─

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

/**
 * Active return unit (in-flight pickup) — mirrors OMS3OrderDataUtils.getActiveReturnUnit /
 * get_post_fulfillment_data_by_tracking_id.groovy.
 */
def getActiveReturnUnit(unit) {
    def pickupStatuses = ['APPROVED', 'IN_PROGRESS']
    def childUnits = unit?.childUnits ?: [:]

    return childUnits.values().find { child ->
        def flow    = child?.flow?.toString()?.toUpperCase()
        def subType = child?.unitSubType?.toString()?.toUpperCase()
        def status  = child?.status?.toString()?.toUpperCase()

        (flow == 'RETURN' && subType == 'PICKUP' && pickupStatuses.contains(status)) ||
        (flow == 'FORWARD' && subType in ['REPLACEMENT', 'EXCHANGE'] && pickupStatuses.contains(status))
    }
}

/**
 * When pickup is terminal (COMPLETED/CANCELLED), active filter no longer matches — resolve any
 * RETURN+PICKUP child, or FORWARD REPLACEMENT/EXCHANGE child, for status evaluation.
 */
def resolveReturnUnitForParent(parentUnit) {
    if (!parentUnit) {
        return null
    }
    def active = getActiveReturnUnit(parentUnit)
    if (active) {
        return active
    }
    def childUnits = parentUnit.childUnits
    def values = []
    if (childUnits instanceof Map) {
        values = childUnits.values() as List
    } else if (childUnits instanceof List) {
        values = childUnits
    }
    def pickup = values.find { child ->
        child?.flow?.toString()?.toUpperCase() == 'RETURN' &&
                child?.unitSubType?.toString()?.toUpperCase() == 'PICKUP'
    }
    if (pickup) {
        return pickup
    }
    return values.find { child ->
        def flow = child?.flow?.toString()?.toUpperCase()
        def subType = child?.unitSubType?.toString()?.toUpperCase()
        flow == 'FORWARD' && subType in ['REPLACEMENT', 'EXCHANGE']
    }
}

def statusNorm = { unit ->
    unit?.status?.toString()?.toLowerCase() ?: ''
}

def isReturnUnitCompleted = { unit ->
    COMPLETED_STATUSES.contains(statusNorm(unit))
}

def isReturnUnitCancelled = { unit ->
    CANCELLED_STATUSES.contains(statusNorm(unit))
}

// ── load data from previous node ───────────────────────────────────────────

def fetchResult = _global?.fetch_order_oxford
if (!fetchResult) {
    throw new Exception("fetch_order_oxford output not found in workflow context — ensure extract_oxford_order_units ran before this node")
}

def unitsMap = fetchResult.units ?: [:]
def allUnitsFlat = fetchResult.allUnitsFlat
if (allUnitsFlat == null && unitsMap) {
    allUnitsFlat = flattenUnitsRecursive(unitsMap)
}
if (!allUnitsFlat) {
    allUnitsFlat = [:]
}

def orderDetails = _global?.orderDetails ?: []
def targetUnitIds = orderDetails.collect { it?.orderItemUnitId?.toString() }.findAll { it }
if (targetUnitIds.isEmpty()) {
    throw new Exception("No orderItemUnitId entries in _global.orderDetails")
}

def returnUnits = targetUnitIds.collect { uid ->
    def parent = resolveUnitByOrderItemUnitId(unitsMap, allUnitsFlat, uid)
    if (!parent) {
        throw new Exception("No unit found in Oxford data for orderItemUnitId: ${uid}")
    }
    def ret = resolveReturnUnitForParent(parent)
    if (!ret) {
        throw new Exception("No return pickup child unit found under delivery unit orderItemUnitId: ${uid}")
    }
    ret
}

// ── decision tree (mirrors DelayInPickupWorkflow isHappyFlow / isUnHappyFlow, OMS path) ─

if (returnUnits.every { isReturnUnitCompleted(it) }) {
    return [useCaseName: UC_ALL_PICKUP_COMPLETED, ended: true]
}

if (returnUnits.every { isReturnUnitCancelled(it) }) {
    return [useCaseName: UC_ALL_CANCELLED, ended: true]
}

return [useCaseName: UC_RUNNING, ended: false]
