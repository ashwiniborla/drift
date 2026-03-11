/**
 * send_reachout_message.groovy
 *
 * Placeholder: logs that a reachout message would be sent.
 * Replace this GROOVY node with an HTTP node when the reachout API is ready.
 */

def reachoutParams = _global?.prepare_reachout_request?.reachout_params ?: [:]
def reachoutTemplate = _global?.prepare_reachout_request?.reachout_template ?: ''

return [reachout_sent: true, reachout_params: reachoutParams, reachout_template: reachoutTemplate]
