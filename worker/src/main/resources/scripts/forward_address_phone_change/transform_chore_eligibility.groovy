/**
 * transform_chore_eligibility.groovy
 *
 * Transforms the Chore address-change eligibility API response into a
 * flat map consumed by the branch_chore_eligible node.
 *
 * API: POST /api/v3/eligibility/addressChangeEligibility
 *
 * Context:
 *   _response – raw HTTP response from Chore eligibility API
 *   _global.check_minions_eligibility.eligibleUnitIds – unit IDs we sent in the request
 *
 * Returns:
 *   [
 *     choreId          : "CU...",
 *     actionableUnitIds: [ list of post-dispatch actionable unit IDs ],
 *     isEligible       : true | false
 *   ]
 */

if (_response == null) {
    throw new Exception("Chore eligibility API response is null")
}

def choreId = _response?.choreId?.toString()

// Check unitEligibilityList for the first unit — eligible if it contains CHANGE_ADDRESS_TEXT
def unitEligibilityList = _response?.unitEligibilityList ?: []
def firstUnitEntry = unitEligibilityList?.getAt(0)
def eligibleOptions = (firstUnitEntry?.eligibleOptions ?: []).collect { it?.toString() }

def isEligible = eligibleOptions.contains('CHANGE_ADDRESS_TEXT')

// Collect all unit IDs from the eligibility list that have the required option
def actionableUnitIds = unitEligibilityList
        .findAll { entry ->
            (entry?.eligibleOptions ?: []).collect { it?.toString() }.contains('CHANGE_ADDRESS_TEXT')
        }
        .collect { it?.unitId?.toString() }
        .findAll { it }

return [
    choreId          : choreId,
    actionableUnitIds: actionableUnitIds,
    isEligible       : isEligible
]
