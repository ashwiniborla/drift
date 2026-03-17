// Reads get_current_address from workflow context and builds inputOptions
// for ask_alternate_phone INSTRUCTION node.
// Uses if/else only on the fields that differ when alt phone is present vs absent.

def addressData = _global?.get_current_address

def customerName = addressData?.name?.input ?: ''

def communications = addressData?.communications ?: []
def primaryPhone = communications.find { it?.type == 'mobile' }?.input ?: ''
def altPhoneRaw  = communications.find { it?.type == 'alt_phone' }?.input

def hasAltPhone = (altPhoneRaw != null && altPhoneRaw != '')

// Format phone numbers with +91 prefix if not already present
def formatPhone = { String phone ->
    if (!phone) return ''
    phone = phone.trim()
    if (phone.startsWith('+')) return phone
    if (phone.startsWith('91') && phone.length() > 10) return '+' + phone
    return '+91' + phone
}

def primaryPhoneFormatted = formatPhone(primaryPhone)
def altPhoneFormatted     = hasAltPhone ? formatPhone(altPhoneRaw) : ''
// Raw digits only (no country code) for input field defaultValues
def primaryPhoneDigits = primaryPhone.replaceAll(/^\+?91/, '')
def altPhoneDigits     = hasAltPhone ? altPhoneRaw.replaceAll(/^\+?91/, '') : ''

// -----------------------------------------------------------------------
// Build inputOptions
// -----------------------------------------------------------------------
def inputOptions = []

// --- Common: header ---
inputOptions << [
    id: 'request_raised_header',
    description: 'Header',
    tags: [values: ['ss.static_text']],
    instructions: [[
        templateId: 'iris_static_message',
        templateVariables: [
            irisKey: 'request_raised_header',
            defaultText: 'Request raised'
        ]
    ]]
]

// --- Common: success popup title ---
inputOptions << [
    id: 'request_raised_title',
    description: 'We have raised request to delivery team',
    tags: [values: ['ss.static_text', 'ss.success_popup']],
    instructions: [[
        templateId: 'iris_static_message',
        templateVariables: [
            irisKey: 'request_raised_to_delivery_team_message',
            defaultText: "We've raised your request to the delivery team"
        ]
    ]]
]

// --- Common: success popup subtitle ---
inputOptions << [
    id: 'request_raised_subtitle',
    description: 'Sorry for inconvenience caused',
    tags: [values: ['ss.static_text', 'ss.success_popup']],
    instructions: [[
        templateId: 'iris_static_message',
        templateVariables: [
            irisKey: 'sorry_for_inconvenience_caused',
            defaultText: 'Sorry for the inconvenience caused'
        ]
    ]]
]

// --- Common: main title ---
inputOptions << [
    id: 'request_raised_title',
    description: 'Title',
    tags: [values: ['ss.static_text']],
    instructions: [[
        templateId: 'iris_static_message',
        templateVariables: [
            irisKey: 'delivery_partner_cant_reach_title',
            defaultText: "Sometimes this happens if the delivery partner can't reach you"
        ]
    ]]
]

// --- Conditional: subtitle (irisKey/defaultText differs by scenario) ---
if (hasAltPhone) {
    inputOptions << [
        id: 'request_raised_subtitle',
        description: 'Subtitle',
        tags: [values: ['ss.static_text']],
        instructions: [[
            templateId: 'iris_static_message',
            templateVariables: [
                irisKey: 'confirm_contact_details_subtitle',
                defaultText: 'Please confirm the contact details'
            ]
        ]]
    ]
} else {
    inputOptions << [
        id: 'request_raised_subtitle',
        description: 'Subtitle',
        tags: [values: ['ss.static_text']],
        instructions: [[
            templateId: 'iris_static_message',
            templateVariables: [
                irisKey: 'confirm_contact_and_add_alternate_subtitle',
                defaultText: 'Please confirm the contact details and add an alternate number'
            ]
        ]]
    ]
}

// --- Conditional: primary contact card (tags + templateVariables differ) ---
if (hasAltPhone) {
    inputOptions << [
        id: 'primary_contact_card',
        description: 'Primary and alternate contact card',
        tags: [values: ['ss.contact_card_primary_secondary']],
        instructions: [[
            templateId: 'contact_card',
            templateVariables: [
                name: customerName,
                phone: primaryPhoneFormatted,
                alternatePhone: altPhoneFormatted
            ]
        ]]
    ]
} else {
    inputOptions << [
        id: 'primary_contact_card',
        description: 'Primary contact card',
        tags: [values: ['ss.contact_card']],
        instructions: [[
            templateId: 'contact_card',
            templateVariables: [
                name: customerName,
                phone: primaryPhoneFormatted
            ]
        ]]
    ]
}

// --- Conditional: add_alternate_number_row only when NO alt phone ---
if (!hasAltPhone) {
    inputOptions << [
        id: 'add_alternate_number_row',
        description: 'Add alternate number (UI opens modal; no API)',
        tags: [values: ['ss.contact_card_button']],
        instructions: [[
            templateId: 'contact_card_button',
            templateVariables: [
                titleIrisKey: 'add_alternate_number_title',
                subtitleIrisKey: 'add_alternate_number_subtitle'
            ]
        ]],
        possibleValues: [[
            displayValue: 'Add alternate number',
            metaData: null,
            value: 'OPEN_ADD_ALTERNATE_NUMBER_MODAL'
        ]]
    ]
}

// --- Common: confirm details button ---
inputOptions << [
    id: 'confirm_details_button',
    description: 'Confirm details CTA',
    tags: [values: ['ss.button_widget', 'ss.next_request_data']],
    possibleValues: [[
        displayValue: 'I confirm my details are correct',
        metaData: [irisKey: 'confirm_details_button'],
        value: 'CONFIRM_DETAILS'
    ]]
]

// --- Conditional: close button only when alt phone IS present ---
if (hasAltPhone) {
    inputOptions << [
        id: 'close_button_widget',
        description: 'Close main screen',
        tags: [values: ['ss.button_widget']],
        possibleValues: [[
            displayValue: 'Close',
            metaData: null,
            value: 'CLOSE'
        ]]
    ]
}

// --- Common: modal fields (parentId = add_alternate_number_row) ---
inputOptions << [
    id: 'change_or_add_number_title',
    parentId: 'add_alternate_number_row',
    description: 'Modal title',
    tags: [values: ['ss.static_text']],
    instructions: [[
        templateId: 'iris_static_message',
        templateVariables: [
            irisKey: 'change_or_add_number_title',
            defaultText: 'Change or add number'
        ]
    ]]
]

inputOptions << [
    id: 'reciever_name',
    parentId: 'add_alternate_number_row',
    description: 'Receiver name',
    tags: [values: ['ss.text_input']],
    instructions: [[
        templateId: 'input_field',
        templateVariables: [
            displayValue: "Receiver's name",
            defaultValue: customerName,
            irisKey: 'phone_number_receiver_name'
        ]
    ]]
]

inputOptions << [
    id: 'reciever_phone_number',
    parentId: 'add_alternate_number_row',
    description: "Receiver's phone number",
    tags: [values: ['ss.phone_input']],
    instructions: [[
        templateId: 'input_field',
        templateVariables: [
            displayValue: "Receiver's phone number",
            defaultValue: primaryPhoneDigits,
            irisKey: 'phone_number_receiver_number'
        ]
    ]]
]

// Conditional: alternate phone defaultValue is empty when not present, actual value when present
inputOptions << [
    id: 'reciever_alternate_phone_number',
    parentId: 'add_alternate_number_row',
    description: 'Alternate phone number',
    tags: [values: ['ss.phone_input', 'ss.next_request_data']],
    instructions: [[
        templateId: 'input_field',
        templateVariables: [
            displayValue: 'Alternate phone number',
            defaultValue: altPhoneDigits,
            irisKey: 'phone_number_alternate_number'
        ]
    ]]
]

inputOptions << [
    id: 'delivery_communications_info',
    parentId: 'add_alternate_number_row',
    description: 'Info banner',
    tags: [values: ['ss.static_text']],
    instructions: [[
        templateId: 'iris_static_message',
        templateVariables: [
            irisKey: 'delivery_communications_shared_on_same_number',
            defaultText: 'Delivery communications will be sent to these numbers'
        ]
    ]]
]

inputOptions << [
    id: 'update_button_widget',
    parentId: 'add_alternate_number_row',
    description: 'Update (modal)',
    tags: [values: ['ss.button_widget', 'ss.next_request_data']],
    possibleValues: [[
        displayValue: 'Update',
        metaData: [irisKey: 'update_button'],
        value: 'update'
    ]]
]

return [inputOptions: inputOptions]
