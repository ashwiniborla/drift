/**
 * transform_chore_feasibility.groovy
 *
 * Transforms the Chore feasibility API response into a flat map
 * consumed by the branch_feasibility node.
 *
 * API: POST /api/v3/order/changeAddressFeasibility/v2
 *
 * Response shape:
 *   {
 *     "choreId": "CH...",
 *     "status": "SUCCESS",
 *     "actionableUnitToPromiseMap": { "<unitId>": <promiseTimestamp>, ... },
 *     "nonActionableUnitToReasonMap": { "<unitId>": "<reason>", ... },
 *     "linkedUnits": []
 *   }
 *
 * Returns:
 *   [
 *     actionableUnitIds   : [ list of unit IDs that are feasible ],
 *     nonActionableUnitIds: [ list of unit IDs that are not feasible ],
 *     isFeasible          : true | false
 *   ]
 */

if (_response == null) {
    throw new Exception("Chore feasibility API response is null")
}

def actionableUnits    = (_response?.actionableUnitToPromiseMap?.keySet() ?: []).toList()
def nonActionableUnits = (_response?.nonActionableUnitToReasonMap?.keySet() ?: []).toList()

// Feasible only when status is SUCCESS and there are no non-actionable units
def isFeasible = _response?.status?.toString() == 'SUCCESS' && nonActionableUnits.isEmpty() && !actionableUnits.isEmpty()

return [
    actionableUnitIds   : actionableUnits,
    nonActionableUnitIds: nonActionableUnits,
    isFeasible          : isFeasible,
    choreId             : _response.choreId
]
