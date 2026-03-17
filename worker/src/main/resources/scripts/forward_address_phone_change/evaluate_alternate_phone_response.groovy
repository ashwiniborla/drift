/**
 * Evaluates ask_alternate_phone viewResponse to determine if savePhone should be true.
 * Reads from _global['ask_alternate_phone:viewResponse']?.selectedOptions.
 *
 * Logic:
 * - If confirm_details_button == "CONFIRM_DETAILS" → savePhone = false
 * - Else if reciever_alternate_phone_number != null AND update_button_widget == "UPDATE" → savePhone = true
 * - Else → savePhone = false
 */
def selectedOptions = _global['ask_alternate_phone:viewResponse']?.selectedOptions

def confirmDetails = selectedOptions?.confirm_details_button?.toString()?.trim()
def altPhone = selectedOptions?.reciever_alternate_phone_number?.toString()?.trim()
def updateButton = selectedOptions?.update_button_widget?.toString()?.trim()

def savePhone = false
if (confirmDetails == 'CONFIRM_DETAILS') {
    savePhone = false
} else if (altPhone != null && altPhone != '' && 'UPDATE'.equalsIgnoreCase(updateButton ?: '')) {
    savePhone = true
}

return [savePhone: savePhone]
