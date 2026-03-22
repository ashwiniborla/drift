/**
 * prepare_location_instruction.groovy
 *
 * Builds the inputOptions array for the confirm_delivery_location_screen INSTRUCTION node.
 *
 * Context:
 *   _global.get_current_address.address.addressLine1.input  – address line 1
 *   _global.get_current_address.address.addressLine2.input  – address line 2
 *   _global.get_current_address.address.landmark.input      – landmark
 *   _global.get_current_address.address.city.input          – city
 *   _global.get_current_address.address.pincode.input       – pincode
 *   _global.get_current_address.address.state.input         – state name
 *   _global.get_current_address.address.stateCode.input     – state code (e.g. IN-KA)
 *   _global.get_current_address.address.country.input       – country (e.g. IN)
 *   _global.get_current_address.address.locationTypeTag     – address type tag (e.g. Home)
 *   _global.evaluate_location.nudge.deepLink                – deeplink URL for location sharing
 *
 * evaluate_location HTTP body is built entirely by scripts/fetch_location/build_evaluate_location_http_body.groovy
 */

def addr       = _global?.get_current_address?.address
def addressLine1  = addr?.addressLine1?.input?.toString() ?: ''
def addressLine2  = addr?.addressLine2?.input?.toString() ?: ''
def landmark      = addr?.landmark?.input?.toString() ?: ''
def city          = addr?.city?.input?.toString() ?: ''
def pincode       = addr?.pincode?.input?.toString() ?: ''
def state         = addr?.state?.input?.toString() ?: ''
def stateCode     = addr?.stateCode?.input?.toString() ?: ''
def country       = addr?.country?.input?.toString() ?: ''
def locationTypeTag = addr?.locationTypeTag?.toString() ?: ''
def deepLink      = _global?.evaluate_location?.nudge?.deepLink?.toString() ?: ''

def inputOptions = [
    [
        id: 'request_raised_header',
        description: 'Header',
        tags: [values: ['ss.static_text']],
        instructions: [
            [
                templateId: 'iris_static_message',
                templateVariables: [
                    irisKey: 'request_raised_header',
                    defaultText: 'Request raised'
                ]
            ]
        ]
    ],
    [
        id: 'request_raised_title',
        description: 'Sorry for inconvenience, we have informed the team',
        tags: [values: ['ss.static_text', 'ss.success_popup']],
        instructions: [
            [
                templateId: 'iris_static_message',
                templateVariables: [
                    irisKey: 'sorry_inconvenience_informed_team',
                    defaultText: 'Sorry for the inconvenience. We\'ve informed the team.'
                ]
            ]
        ]
    ],
    [
        id: 'change_addressstatic_location',
        description: 'Location placeholder',
        tags: [values: ['ss.static_location_tag']],
        instructions: []
    ],
    [
        id: 'delivery_location_section_title',
        description: 'Delivery partner location section title',
        tags: [values: ['ss.static_text']],
        instructions: [
            [
                templateId: 'iris_static_message',
                templateVariables: [
                    irisKey: 'delivery_partner_trouble_finding_location',
                    defaultText: 'Delivery partner might be having trouble finding your location.'
                ]
            ]
        ]
    ],
    [
        id: 'delivery_location_section_subtitle',
        description: 'Share precise location hint',
        tags: [values: ['ss.static_text']],
        instructions: [
            [
                templateId: 'iris_static_message',
                templateVariables: [
                    irisKey: 'share_precise_location_hint',
                    defaultText: 'Share your precise location to avoid the navigation guesswork.'
                ]
            ]
        ]
    ],
    [
        id: 'delivery_location_display',
        description: 'Static delivery location (map + address)',
        tags: [values: ['ss.static_location']],
        instructions: [
            [
                templateId: 'static_location',
                templateVariables: [
                    addressLine1  : addressLine1,
                    addressLine2  : addressLine2,
                    landmark      : landmark,
                    city          : city,
                    pincode       : pincode,
                    state         : state,
                    stateCode     : stateCode,
                    country       : country,
                    locationTypeTag: locationTypeTag
                ]
            ]
        ]
    ],
    [
        id: 'submit_location_button',
        description: 'Submit location confirmation',
        tags: [values: ['ss.take_location_button']],
        possibleValues: [
            [
                displayValue: 'Submit',
                metaData: [
                    url    : deepLink,
                    irisKey: 'submit_button'
                ],
                value: 'SUBMIT'
            ]
        ]
    ]
]

return [inputOptions: inputOptions]
