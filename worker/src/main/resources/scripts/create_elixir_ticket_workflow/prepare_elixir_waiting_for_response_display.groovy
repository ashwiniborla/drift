/**
 * Builds inputOptions for elixir_waiting_for_response (dynamic possibleDynamicValues).
 * Surfaces post-fulfillment / tracking data from elixir_get_order_details for template elixir_ticket_details.
 *
 * Reads: _global.elixir_get_order_details.postFulfillmentData
 *
 * @return Map with inputOptions (List of one Option for imsv2.info + elixir_ticket_details)
 */
def pfd = _global?.elixir_get_order_details?.postFulfillmentData

def inputOptions = [
        [
                id         : 'elixir_pfd_k7n2',
                description: 'Post-fulfillment tracking (Elixir ticket details)',
                tags       : [values: ['imsv2.info']],
                instructions: [
                        [
                                templateId       : 'elixir_ticket_details',
                                templateVariables: [
                                        postFulfillmentData: pfd ?: [:]
                                ]
                        ]
                ]
        ]
]

return [inputOptions: inputOptions]
