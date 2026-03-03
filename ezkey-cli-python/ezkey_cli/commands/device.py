"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Device Command
Description: Device simulation commands for enrollment and authentication testing
"""

import click

from ..config import ConfigManager
from ..utils import HttpClient, JsonUtils, OutputUtils
from ..utils.device_storage import DeviceStorage


@click.group(name='device')
@click.pass_context
def device_group(ctx):
    """
    Device simulation commands for testing enrollment and authentication flows.

    Warning: Device private keys are stored in plaintext for development/testing purposes.
    """
    pass


@device_group.command('enroll')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID from QR code or admin API')
@click.option('--enrollment-proof-token', required=True, help='Enrollment proof token from QR code or admin API')
@click.option('--challenge', required=True, type=int, help='Challenge response for enrollment verification')
@click.pass_context
def enroll_device(ctx, enrollment_id, enrollment_proof_token, challenge):
    """
    Enroll device by binding and verifying with integration.

    This simulates the device enrollment flow:
    1. BIND: Initiate binding with enrollment proof token
    2. Generate EC P-256 keypair for device
    3. Sign enrollment proof token with device private key
    4. VERIFY: Complete enrollment with signed proof token

    Example:
      $ ezkey device enroll --enrollment-id 456 --enrollment-proof-token "EZK-ABC123" --challenge 123456
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    storage = DeviceStorage()

    # Check if device already enrolled
    if storage.device_exists(enrollment_id):
        OutputUtils.error(f"Device with enrollment ID {enrollment_id} is already enrolled.")
        OutputUtils.info("Use 'ezkey device remove --enrollment-id {enrollment_id}' to remove it first.")
        return

    # Get URLs
    auth_url = config.get('authUrl')
    crypto_url = config.get('cryptoUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
        return
    if not crypto_url:
        OutputUtils.error("Crypto URL not configured. Use 'ezkey configure set --crypto-url <url>'")
        return

    try:
        # Step 1: BIND enrollment
        OutputUtils.info(f"Step 1/4: Binding enrollment {enrollment_id}...")
        bind_url = f"{auth_url}/api/v1/enrollments/bind"
        bind_payload = {
            "enrollmentId": enrollment_id,
            "enrollmentProofToken": enrollment_proof_token
        }

        OutputUtils.verbose(f"POST {bind_url}", verbose)
        OutputUtils.verbose(f"Request: {JsonUtils.format_output(bind_payload, pretty_print=verbose)}", verbose)

        bind_response = http_client.post(bind_url, json_data=bind_payload)

        if not bind_response.success:
            OutputUtils.error(f"Enrollment bind failed: {bind_response.error}")
            return

        bind_data = bind_response.data
        OutputUtils.verbose(f"Response: {JsonUtils.format_output(bind_data, pretty_print=verbose)}", verbose)
        OutputUtils.success("✓ Enrollment bound successfully")

        # Step 2: Generate device keypair
        OutputUtils.info("Step 2/4: Generating device keypair...")
        keypair_url = f"{crypto_url}/api/v1/crypto/keypair"

        OutputUtils.verbose(f"GET {keypair_url}", verbose)

        keypair_response = http_client.get(keypair_url)

        if not keypair_response.success:
            OutputUtils.error(f"Keypair generation failed: {keypair_response.error}")
            return

        keypair_data = keypair_response.data
        device_private_key = keypair_data['privateKey']
        device_public_key = keypair_data['publicKey']

        OutputUtils.verbose(f"Device public key: {device_public_key[:50]}...", verbose)
        OutputUtils.success("✓ Device keypair generated")

        # Step 3: Sign enrollment proof token
        OutputUtils.info("Step 3/4: Signing enrollment proof token...")
        sign_url = f"{crypto_url}/api/v1/crypto/sign"
        sign_payload = {
            "data": enrollment_proof_token,
            "privateKey": device_private_key
        }

        OutputUtils.verbose(f"POST {sign_url}", verbose)

        sign_response = http_client.post(sign_url, json_data=sign_payload)

        if not sign_response.success:
            OutputUtils.error(f"Signing failed: {sign_response.error}")
            return

        sign_data = sign_response.data
        enrollment_proof_token_signed = sign_data['signature']

        OutputUtils.verbose(f"Signature: {enrollment_proof_token_signed[:50]}...", verbose)
        OutputUtils.success("✓ Enrollment proof token signed")

        # Step 4: VERIFY enrollment
        OutputUtils.info("Step 4/4: Verifying enrollment...")
        verify_url = f"{auth_url}/api/v1/enrollments/verify"
        verify_payload = {
            "enrollmentId": enrollment_id,
            "challengeResponse": challenge,
            "devicePublicKey": device_public_key,
            "enrollmentProofTokenSigned": enrollment_proof_token_signed
        }

        OutputUtils.verbose(f"POST {verify_url}", verbose)
        OutputUtils.verbose(f"Request: {JsonUtils.format_output(verify_payload, pretty_print=verbose)}", verbose)

        verify_response = http_client.post(verify_url, json_data=verify_payload)

        if not verify_response.success:
            OutputUtils.error(f"Enrollment verification failed: {verify_response.error}")
            return

        verify_data = verify_response.data
        OutputUtils.verbose(f"Response: {JsonUtils.format_output(verify_data, pretty_print=verbose)}", verbose)

        if not verify_data.get('active', False):
            OutputUtils.error("Enrollment verification returned active=false")
            return

        OutputUtils.success("✓ Enrollment verified and activated")

        # Save device data
        device_data = {
            "enrollmentId": enrollment_id,
            "enrollmentProofToken": enrollment_proof_token,
            "devicePrivateKey": device_private_key,
            "devicePublicKey": device_public_key,
            "integrationPublicKey": bind_data.get('integrationPublicKey'),
            "integrationName": bind_data.get('integrationName'),
            "integrationDescription": bind_data.get('integrationDescription'),
            "integrationLogo": bind_data.get('integrationLogo'),
            "enrollmentName": bind_data.get('enrollmentName'),
            "active": True
        }

        storage.save_device(device_data)

        OutputUtils.info("")
        OutputUtils.success(f"Device enrolled successfully!")
        OutputUtils.info(f"Enrollment ID: {enrollment_id}")
        OutputUtils.info(f"Integration: {bind_data.get('integrationName', 'N/A')}")
        OutputUtils.info(f"Enrollment Name: {bind_data.get('enrollmentName', 'N/A')}")
        OutputUtils.info("")
        OutputUtils.info("Use 'ezkey device auth --enrollment-id {enrollment_id}' to authenticate")

    except Exception as e:
        OutputUtils.error(f"Enrollment failed: {str(e)}")
        if verbose:
            import traceback
            OutputUtils.error(traceback.format_exc())


@device_group.command('auth')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID of device')
@click.option('--approve', 'action', flag_value='approve', default=True, help='Approve authentication (default)')
@click.option('--deny', 'action', flag_value='deny', help='Deny authentication')
@click.option('--challenge', type=int, help='Challenge response if required by integration')
@click.pass_context
def authenticate_device(ctx, enrollment_id, action, challenge):
    """
    Check for pending authentication and respond.

    This simulates the device authentication flow:
    1. Generate device proof token
    2. Sign device proof token with device private key
    3. PENDING: Check for pending authentication attempts
    4. Validate integration signature on auth attempt proof token
    5. Sign auth attempt proof token with device private key
    6. RESPOND: Submit authentication response

    Example:
      $ ezkey device auth --enrollment-id 456
      $ ezkey device auth --enrollment-id 456 --deny
      $ ezkey device auth --enrollment-id 456 --challenge 123456
    """
    config: ConfigManager = ctx.obj['config']
    http_client = HttpClient(config)
    verbose = ctx.obj.get('verbose', False)
    pretty_print = ctx.obj.get('pretty_print', True)
    storage = DeviceStorage()

    # Load device data
    device_data = storage.load_device(enrollment_id)
    if device_data is None:
        OutputUtils.error(f"Device with enrollment ID {enrollment_id} not found.")
        OutputUtils.info("Use 'ezkey device enroll' to enroll a device first.")
        return

    # Get URLs
    auth_url = config.get('authUrl')
    crypto_url = config.get('cryptoUrl')
    if not auth_url:
        OutputUtils.error("Auth URL not configured. Use 'ezkey configure set --auth-url <url>'")
        return
    if not crypto_url:
        OutputUtils.error("Crypto URL not configured. Use 'ezkey configure set --crypto-url <url>'")
        return

    try:
        # Step 1: Generate device proof token
        OutputUtils.info("Step 1/6: Generating device proof token...")
        prooftoken_url = f"{crypto_url}/api/v1/crypto/prooftoken"

        OutputUtils.verbose(f"GET {prooftoken_url}", verbose)

        prooftoken_response = http_client.get(prooftoken_url)

        if not prooftoken_response.success:
            OutputUtils.error(f"Proof token generation failed: {prooftoken_response.error}")
            return

        device_proof_token = prooftoken_response.data['proofToken']
        OutputUtils.verbose(f"Device proof token: {device_proof_token}", verbose)
        OutputUtils.success("✓ Device proof token generated")

        # Step 2: Sign device proof token
        OutputUtils.info("Step 2/6: Signing device proof token...")
        sign_url = f"{crypto_url}/api/v1/crypto/sign"
        sign_payload = {
            "data": device_proof_token,
            "privateKey": device_data['devicePrivateKey']
        }

        OutputUtils.verbose(f"POST {sign_url}", verbose)

        sign_response = http_client.post(sign_url, json_data=sign_payload)

        if not sign_response.success:
            OutputUtils.error(f"Signing failed: {sign_response.error}")
            return

        device_proof_token_signed = sign_response.data['signature']
        OutputUtils.verbose(f"Signature: {device_proof_token_signed[:50]}...", verbose)
        OutputUtils.success("✓ Device proof token signed")

        # Step 3: Check for pending authentication
        OutputUtils.info("Step 3/6: Checking for pending authentication...")
        pending_url = f"{auth_url}/api/v1/auth-attempts/pending"
        pending_payload = {
            "enrollmentId": enrollment_id,
            "enrollmentProofToken": device_data['enrollmentProofToken'],
            "deviceProofToken": device_proof_token,
            "deviceProofTokenSigned": device_proof_token_signed
        }

        OutputUtils.verbose(f"POST {pending_url}", verbose)
        OutputUtils.verbose(f"Request: {JsonUtils.format_output(pending_payload, pretty_print=verbose)}", verbose)

        pending_response = http_client.post(pending_url, json_data=pending_payload)

        if not pending_response.success:
            # Check for 204 No Content (no pending attempts)
            if pending_response.status == 204:
                OutputUtils.info("No pending authentication attempts.")
                return
            OutputUtils.error(f"Pending check failed: {pending_response.error}")
            return

        if pending_response.status == 204 or not pending_response.data:
            OutputUtils.info("No pending authentication attempts.")
            return

        pending_data = pending_response.data
        if not isinstance(pending_data, dict):
            OutputUtils.info("No pending authentication attempts.")
            return

        OutputUtils.verbose(f"Response: {JsonUtils.format_output(pending_data, pretty_print=verbose)}", verbose)

        auth_attempt_id = pending_data['authAttemptId']
        auth_attempt_proof_token = pending_data['authAttemptProofToken']
        auth_attempt_proof_token_signed_by_integration = pending_data[
            'authAttemptProofTokenSignedByIntegration'
        ]
        auth_attempt_challenge_required = pending_data.get('authAttemptChallengeRequired', False)

        OutputUtils.success(f"✓ Found pending authentication attempt: {auth_attempt_id}")

        # Check if challenge is required
        if auth_attempt_challenge_required and challenge is None:
            OutputUtils.error("Challenge response is required but not provided.")
            OutputUtils.info("Use --challenge <value> to provide challenge response")
            return

        # Step 4: Validate integration signature
        OutputUtils.info("Step 4/6: Validating integration signature...")
        validate_url = f"{crypto_url}/api/v1/crypto/validate"
        validate_payload = {
            "data": auth_attempt_proof_token,
            "signature": auth_attempt_proof_token_signed_by_integration,
            "publicKey": device_data['integrationPublicKey']
        }

        OutputUtils.verbose(f"POST {validate_url}", verbose)

        validate_response = http_client.post(validate_url, json_data=validate_payload)

        if not validate_response.success:
            OutputUtils.error(f"Signature validation failed: {validate_response.error}")
            return

        is_valid = validate_response.data.get('valid', False)
        if not is_valid:
            OutputUtils.error("Integration signature is invalid!")
            return

        OutputUtils.success("✓ Integration signature validated")

        # Step 5: Sign auth attempt proof token
        OutputUtils.info("Step 5/6: Signing auth attempt proof token...")
        sign_attempt_payload = {
            "data": auth_attempt_proof_token,
            "privateKey": device_data['devicePrivateKey']
        }

        OutputUtils.verbose(f"POST {sign_url}", verbose)

        sign_attempt_response = http_client.post(sign_url, json_data=sign_attempt_payload)

        if not sign_attempt_response.success:
            OutputUtils.error(f"Signing failed: {sign_attempt_response.error}")
            return

        auth_attempt_proof_token_signed_by_device = sign_attempt_response.data['signature']
        OutputUtils.verbose(f"Signature: {auth_attempt_proof_token_signed_by_device[:50]}...", verbose)
        OutputUtils.success("✓ Auth attempt proof token signed")

        # Step 6: Respond to authentication
        action_approved = (action == 'approve')
        action_text = "Approving" if action_approved else "Denying"
        OutputUtils.info(f"Step 6/6: {action_text} authentication...")

        respond_url = f"{auth_url}/api/v1/auth-attempts/respond"
        respond_payload = {
            "authAttemptId": auth_attempt_id,
            "authAttemptAccepted": action_approved,
            "authAttemptProofTokenSignedByDevice": auth_attempt_proof_token_signed_by_device
        }

        if auth_attempt_challenge_required and challenge is not None:
            respond_payload["authAttemptChallengeResponse"] = challenge

        OutputUtils.verbose(f"POST {respond_url}", verbose)
        OutputUtils.verbose(f"Request: {JsonUtils.format_output(respond_payload, pretty_print=verbose)}", verbose)

        respond_response = http_client.post(respond_url, json_data=respond_payload)

        if not respond_response.success:
            OutputUtils.error(f"Response submission failed: {respond_response.error}")
            return

        respond_data = respond_response.data
        OutputUtils.verbose(f"Response: {JsonUtils.format_output(respond_data, pretty_print=verbose)}", verbose)

        result = respond_data.get('result', 'UNKNOWN')
        message = respond_data.get('message', '')

        OutputUtils.info("")
        if result == 'APPROVED':
            OutputUtils.success(f"✓ Authentication APPROVED")
        elif result == 'DENIED':
            OutputUtils.success(f"✓ Authentication DENIED")
        else:
            OutputUtils.info(f"Result: {result}")

        if message:
            OutputUtils.info(f"Message: {message}")

    except Exception as e:
        OutputUtils.error(f"Authentication failed: {str(e)}")
        if verbose:
            import traceback
            OutputUtils.error(traceback.format_exc())


@device_group.command('list')
@click.pass_context
def list_devices(ctx):
    """
    List all enrolled devices.

    Example:
      $ ezkey device list
    """
    storage = DeviceStorage()
    devices = storage.list_devices()

    if not devices:
        OutputUtils.info("No enrolled devices found.")
        OutputUtils.info("Use 'ezkey device enroll' to enroll a device.")
        return

    # Display table
    OutputUtils.info(f"Found {len(devices)} enrolled device(s):\n")

    # Header
    header = f"{'ID':<8} {'Integration':<30} {'Enrollment Name':<30} {'Status':<10}"
    OutputUtils.info(header)
    OutputUtils.info("-" * len(header))

    # Rows
    for device in devices:
        enrollment_id = device.get('enrollmentId', 'N/A')
        integration_name = device.get('integrationName', 'N/A')
        enrollment_name = device.get('enrollmentName', 'N/A')
        active = device.get('active', False)
        status = "Active" if active else "Inactive"

        # Truncate long names
        if len(integration_name) > 29:
            integration_name = integration_name[:26] + "..."
        if len(enrollment_name) > 29:
            enrollment_name = enrollment_name[:26] + "..."

        row = f"{enrollment_id:<8} {integration_name:<30} {enrollment_name:<30} {status:<10}"
        OutputUtils.info(row)

    OutputUtils.info("")
    OutputUtils.info("Use 'ezkey device show --enrollment-id <id>' for details")


@device_group.command('show')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID to display')
@click.pass_context
def show_device(ctx, enrollment_id):
    """
    Show detailed device information.

    Example:
      $ ezkey device show --enrollment-id 456
    """
    storage = DeviceStorage()
    pretty_print = ctx.obj.get('pretty_print', True)

    device_data = storage.load_device(enrollment_id)
    if device_data is None:
        OutputUtils.error(f"Device with enrollment ID {enrollment_id} not found.")
        return

    # Display as formatted JSON
    OutputUtils.output_json(device_data, pretty_print=pretty_print)


@device_group.command('remove')
@click.option('--enrollment-id', required=True, type=int, help='Enrollment ID to remove')
@click.option('--force', is_flag=True, help='Skip confirmation prompt')
@click.pass_context
def remove_device(ctx, enrollment_id, force):
    """
    Remove enrolled device.

    Example:
      $ ezkey device remove --enrollment-id 456
      $ ezkey device remove --enrollment-id 456 --force
    """
    storage = DeviceStorage()

    # Check if device exists
    device_data = storage.load_device(enrollment_id)
    if device_data is None:
        OutputUtils.error(f"Device with enrollment ID {enrollment_id} not found.")
        return

    # Confirmation prompt
    if not force:
        integration_name = device_data.get('integrationName', 'Unknown')
        enrollment_name = device_data.get('enrollmentName', 'Unknown')

        OutputUtils.info(f"Enrollment ID: {enrollment_id}")
        OutputUtils.info(f"Integration: {integration_name}")
        OutputUtils.info(f"Enrollment Name: {enrollment_name}")
        OutputUtils.info("")

        confirm = click.confirm("Are you sure you want to remove this device?", default=False)
        if not confirm:
            OutputUtils.info("Cancelled.")
            return

    # Delete device
    if storage.delete_device(enrollment_id):
        OutputUtils.success(f"Device {enrollment_id} removed successfully.")
    else:
        OutputUtils.error(f"Failed to remove device {enrollment_id}.")


# Make the group available for import
device = device_group
