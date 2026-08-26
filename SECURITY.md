# Security Policy

## Supported versions

Security fixes are provided for the latest released version of Zerion Desktop.

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅        |
| < 1.0   | ❌        |

## Reporting a vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.** Public disclosure of an unpatched issue puts
users at risk.

Instead, report privately using **GitHub Security Advisories**:

1. Go to the **Security** tab of this repository.
2. Click **Report a vulnerability** (Private vulnerability reporting).

If you cannot use that channel, contact the maintainers through the security
contact listed at <https://zerion.chat>.

### What to include

A good report helps us reproduce and fix the issue quickly. Where possible,
please include:

- The affected version, platform (Windows / Linux / Flatpak), and build.
- A clear description of the vulnerability and its impact.
- Step-by-step reproduction instructions or a proof of concept.
- The component involved (messaging, wallet, ZVault, Tor/networking, packaging).
- Any relevant configuration, and whether it requires local access or a
  compromised device.

Please send exploit details, proof-of-concept code, and anything sensitive
**only** through the private channel above, never in a public issue.

## Responsible disclosure

- We will acknowledge your report as quickly as we can and keep you updated on our
  progress.
- Please give us a reasonable period to investigate and release a fix before any
  public disclosure, and coordinate the disclosure timing with us.
- We do not currently run a paid bug-bounty program, but we will credit reporters
  who follow responsible disclosure, unless you prefer to remain anonymous.

## Scope

In scope: the Zerion Desktop application and its source in this repository,
covering messaging, wallets, ZVault, Tor and networking, data-at-rest handling,
and packaging.

Out of scope: issues that require a fully compromised device or physical access to
an unlocked machine (these are outside the threat model; see
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)), and vulnerabilities in third-party
dependencies that are already publicly known and awaiting an upstream fix (though
we still welcome a heads-up).
