[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][Guid]$OrganizationId,
    [string]$DatabaseUrl = 'jdbc:postgresql://127.0.0.1:5432/wautech_crm'
)
$ErrorActionPreference = 'Stop'
if ($DatabaseUrl -notmatch '^jdbc:postgresql://(localhost|127\.0\.0\.1):[0-9]{1,5}/[A-Za-z0-9_]+$') {
    throw 'Only an explicit loopback PostgreSQL URL without options is allowed.'
}
if ($env:SPRING_PROFILES_ACTIVE -and $env:SPRING_PROFILES_ACTIVE -ne 'local-account-setup') {
    throw 'Remove inherited Spring profiles before running this local-only command.'
}
$backend = Join-Path (Split-Path $PSScriptRoot -Parent) 'backend'
Push-Location $backend
try {
    # Build before prompting or changing the database. No application is started by Maven.
    & .\mvnw.cmd -q -DskipTests package dependency:build-classpath '-Dmdep.outputFile=target/local-setup-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Setup build failed.' }
    Write-Host "Database: $DatabaseUrl"
    Write-Host "Organization: $OrganizationId (must already exist and be active)"
    Write-Host 'Account: admin@wautech.local; Local Administrator; enabled; ACTIVE ADMIN'
    Write-Host 'Existing passwords are preserved. Conflicting memberships and disabled users are rejected.'
    $expected = "LOCAL DEVELOPMENT $DatabaseUrl"
    if ((Read-Host "Confirm this is your local DEVELOPMENT database by typing '$expected'") -cne $expected) {
        throw 'Database confirmation did not match. No account changes made.'
    }
    $securePassword = Read-Host 'Initial password (12–72 UTF-8 bytes; ignored if already provisioned)' -AsSecureString
    $secureConfirmation = Read-Host 'Repeat initial password' -AsSecureString
    $passwordPointer = [IntPtr]::Zero
    $confirmationPointer = [IntPtr]::Zero
    $process = $null
    try {
        $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
        $confirmationPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureConfirmation)
        $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
        $plainConfirmation = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($confirmationPointer)
        $byteCount = [Text.Encoding]::UTF8.GetByteCount($plainPassword)
        if ($plainPassword -cne $plainConfirmation -or $byteCount -lt 12 -or $byteCount -gt 72 -or $plainPassword.Contains("`n") -or $plainPassword.Contains("`r")) {
            throw 'Passwords must match, contain no newlines, and have 12–72 UTF-8 bytes.'
        }
        $classpath = (Join-Path $backend 'target/classes') + [IO.Path]::PathSeparator + (Get-Content 'target/local-setup-classpath.txt' -Raw).Trim()
        $start = New-Object Diagnostics.ProcessStartInfo
        $start.FileName = (Get-Command java -ErrorAction Stop).Source
        $start.Arguments = '-cp "' + $classpath + '" com.wautech.localsetup.LocalAccountSetup'
        $start.WorkingDirectory = $backend
        $start.UseShellExecute = $false
        $start.CreateNoWindow = $true
        $start.RedirectStandardInput = $true
        $start.StandardInputEncoding = New-Object Text.UTF8Encoding($false)
        $start.EnvironmentVariables['LOCAL_SETUP_DB_URL'] = $DatabaseUrl
        $start.EnvironmentVariables['LOCAL_SETUP_CONFIRMED_DB_URL'] = $DatabaseUrl
        $start.EnvironmentVariables['LOCAL_SETUP_ORGANIZATION_ID'] = $OrganizationId.ToString()
        $start.EnvironmentVariables['SPRING_PROFILES_ACTIVE'] = 'local-account-setup'
        $process = [Diagnostics.Process]::Start($start)
        $process.StandardInput.WriteLine($plainPassword)
        $process.StandardInput.Close()
        $plainConfirmation = $null
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw 'Local account setup failed.' }
        # Verify the existing application using its normal session and CSRF flow.
        # Keep the password in process memory only; never print HTTP request bodies.
        $baseUrl = 'http://localhost:8080'
        $csrf = Invoke-RestMethod "$baseUrl/api/auth/csrf" -SessionVariable crmSession
        $headers = @{ $csrf.headerName = $csrf.token }
        $loginBody = @{ email = 'admin@wautech.local'; password = $plainPassword } | ConvertTo-Json -Compress
        try {
            $login = Invoke-RestMethod "$baseUrl/api/auth/login" -Method Post -WebSession $crmSession -Headers $headers -ContentType 'application/json' -Body $loginBody
        } catch { throw 'Account setup completed, but API login failed. Existing credentials are never reset; check your entered password and the backend at localhost:8080.' }
        finally { $loginBody = $null; $plainPassword = $null }
        try {
            $me = Invoke-RestMethod "$baseUrl/api/auth/me" -WebSession $crmSession
            if ($me.email -ne 'admin@wautech.local') { throw 'Unexpected authenticated account.' }
            Write-Host 'PASS: authentication API login and /api/auth/me'
            $organizationHeaders = @{ 'X-Organization-ID' = $OrganizationId.ToString() }
            $null = Invoke-RestMethod "$baseUrl/api/companies" -WebSession $crmSession -Headers $organizationHeaders
            Write-Host 'PASS: authorized organization access and GET /api/companies'
        } finally {
            $csrf = Invoke-RestMethod "$baseUrl/api/auth/csrf" -WebSession $crmSession
            $headers = @{ $csrf.headerName = $csrf.token }
            $null = Invoke-RestMethod "$baseUrl/api/auth/logout" -Method Post -WebSession $crmSession -Headers $headers
        }
        $invalidated = $false
        try { $null = Invoke-RestMethod "$baseUrl/api/auth/me" -WebSession $crmSession }
        catch {
            if ([int]$_.Exception.Response.StatusCode -eq 401) { $invalidated = $true }
            else { throw 'Unexpected response while checking session invalidation.' }
        }
        if (-not $invalidated) { throw 'Logout did not invalidate the session.' }
        Write-Host 'PASS: logout invalidates the session'
        Write-Host 'Open http://localhost:5173 and sign in with admin@wautech.local and your chosen password.'
    } finally {
        $plainPassword = $null
        $plainConfirmation = $null
        if ($passwordPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer) }
        if ($confirmationPointer -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($confirmationPointer) }
        $securePassword.Dispose()
        $secureConfirmation.Dispose()
        if ($process) { $process.Dispose() }
    }
} finally { Pop-Location }
