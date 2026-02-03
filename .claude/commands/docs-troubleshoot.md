# Troubleshooting Guide

Load the troubleshooting guide for common errors and issues in MC-ORG.

## Instructions

Read the troubleshooting guide document at:
`documentation/ai/TROUBLESHOOTING_GUIDE.md`

This document contains:

### Compilation Errors
- "Cannot resolve symbol 'createHTML'" - wrong import
- "Type mismatch: inferred type is Result..." - error type mismatch
- "Cannot find method 'select' in SafeSQL" - using constructor
- "Unresolved reference: respondHtml" - missing import
- "Smart cast is impossible" - mutable property issues

### Runtime Errors
- "SQL injection pattern detected" - unsafe query
- "NullPointerException in handler" - null handling
- "403 Forbidden on valid request" - auth plugin issues
- "HTMX not updating the page" - target mismatch
- "Database connection timeout" - connection issues

### Test Failures
- Factory method issues
- Database state contamination
- Assertion failures

### Build Issues
- Maven compilation failures
- Database migration errors

### Deployment Issues
- 502 errors
- Migration failures on startup

## When to Use

- Debugging compilation errors
- Fixing runtime exceptions
- Resolving test failures
- Troubleshooting HTMX issues
- Fixing build problems
