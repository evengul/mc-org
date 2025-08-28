# GitHub Copilot Instructions for MC-ORG Project

## 🎯 Project Overview

MC-ORG is a **Ktor-based Kotlin web application** using **server-side HTML generation** with Kotlin HTML DSL. This is a Minecraft world collaboration platform for managing building projects, tasks, and team coordination.

**CRITICAL**: This application serves HTML responses (not JSON APIs) and uses **HTMX for dynamic interactions**. PUT/PATCH/POST/DELETE endpoints return HTML fragments that replace specific page sections.

## 📚 Required Reading Before Starting Any Task

### 🔴 MANDATORY - Read Before ANY Development Work

**[AI_AGENT_DOCUMENTATION.md](../documentation/ai/AI_AGENT_DOCUMENTATION.md)**
- **When to read**: ALWAYS before starting any task
- **Contains**: Core architecture, technology stack, domain model, routing structure, permission model
- **Critical info**: Ktor 3.0 setup, PostgreSQL with Flyway, JWT auth, HTML DSL patterns, business rules
- **Key sections**: Module hierarchy, domain entities (User, World, Project, Task), role-based permissions

**[AI_INTEGRATION_SPECS.md](../documentation/ai/AI_INTEGRATION_SPECS.md)**
- **When to read**: Before implementing any endpoints or modifying existing code
- **Contains**: Pipeline patterns, error handling, transaction boundaries, build commands
- **Critical info**: Step interface with Result<E, S> pattern, DatabaseSteps.transaction usage, Maven commands
- **Key sections**: Safe modification boundaries, pipeline architecture templates, rollback procedures

### 🟠 ESSENTIAL - Read Before Specific Task Types

**[API_SPECIFICATIONS.md](../documentation/ai/API_SPECIFICATIONS.md)**
- **When to read**: Before implementing any endpoints, forms, or HTMX interactions
- **Contains**: HTMX response patterns, authentication flows, validation rules, template patterns
- **Critical info**: GET returns full pages, PUT/PATCH/POST/DELETE return HTML fragments, pipeline processing
- **Key sections**: Request/response patterns, HTMX integration, form validation, error handling

**[BUSINESS_REQUIREMENTS.md](../documentation/ai/BUSINESS_REQUIREMENTS.md)**
- **When to read**: Before implementing user workflows, permissions, or domain logic
- **Contains**: User roles, project lifecycle, dependency rules, business constraints
- **Critical info**: World access via invitations only, role hierarchy (Owner > Admin > Member > Banned), no circular dependencies
- **Key sections**: Domain model concepts, permission model, core user workflows, business rules

**[CSS_ARCHITECTURE.md](../documentation/ai/CSS_ARCHITECTURE.md)**
- **When to read**: Before modifying any HTML templates or styling
- **Contains**: Component classes, utility patterns, design tokens, refactoring guidelines
- **Critical info**: CSS custom properties (--clr-*, --spacing-*), component class patterns, responsive design
- **Key sections**: Design tokens reference, component classes, HTML refactoring checklist

**[IMPLEMENTATION_PLAN.md](../documentation/ai/IMPLEMENTATION_PLAN.md)**
- **When to read**: Before starting feature implementation or understanding project status
- **Contains**: Feature priorities, implementation patterns, lessons learned, testing requirements
- **Critical info**: Sprint organization, completed vs pending features, mandatory patterns, compilation safety
- **Key sections**: Priority matrix, implementation guidelines, lessons learned from Sprint 1

## 🚨 Critical Development Protocols

### Before Writing ANY Code:
1. **Compile Check**: Run `mvn clean compile` - must pass with zero errors
2. **Pattern Review**: Find similar existing implementations to follow
3. **Test First**: Create failing tests before implementing features
4. **Architecture Compliance**: Follow established patterns from documentation

### Required Patterns for All New Code:

#### Pipeline Architecture (MANDATORY):
```kotlin
suspend fun ApplicationCall.handleFeatureAction() {
    val parameters = this.receiveParameters()
    val user = this.getUser()
    val contextId = this.getContextId()

    executePipeline(
        onSuccess = { result: FeatureResult ->
            respondHtml(createHTML().div {
                featureSuccessContent(result)
            })
        },
        onFailure = { failure: FeatureFailures ->
            when (failure) {
                is FeatureFailures.ValidationError ->
                    respondBadRequest("Validation failed: ${failure.errors.joinToString()}")
                // ...other failure types
            }
        }
    ) {
        step(Step.value(parameters))
            .step(ValidateInputStep)
            .step(ValidatePermissionsStep(user, contextId))
            .step(ExecuteBusinessLogicStep)
            .step(GetUpdatedDataStep)
    }
}
```

#### HTML Response Pattern (MANDATORY):
```kotlin
// ✅ For successful HTMX fragments
respondHtml(createHTML().div {
    div("notice notice--success") {
        +"Operation completed successfully"
    }
    // Updated content here
})

// ✅ For errors
respondBadRequest("Clear, user-friendly error message")
```

### Technology Stack Reminders:
- **Language**: Kotlin 2.1.10 + Java 21
- **Framework**: Ktor 3.0.3 with Netty server
- **Database**: PostgreSQL with Flyway migrations
- **Frontend**: Server-side HTML with Kotlin HTML DSL + HTMX
- **Build**: Maven (NOT Gradle)
- **Shell**: PowerShell (use `;` not `&&` for command chaining)

### Permission Model (ALWAYS Validate):
- **Roles**: Owner (0) > Admin (10) > Member (100) > Banned (1000)
- **World Access**: Private by default, invitation-only
- **Global Roles**: Developer, Support, Moderator for system admin

### Common Import Requirements:
```kotlin
import kotlinx.html.stream.createHTML  // NOT kotlinx.html.createHTML
import app.mcorg.presentation.utils.respondHtml
import app.mcorg.presentation.utils.respondBadRequest
```

## 🧪 Testing Requirements (MANDATORY)

### Before Marking ANY Task Complete:
- [ ] `mvn clean compile` passes with zero errors
- [ ] `mvn test` passes with all existing + new tests
- [ ] Manual verification of happy path completed
- [ ] Error cases properly handled and tested
- [ ] HTML templates render without errors

### Test-First Development Protocol:
1. **Create test structure first** - Mirror src/main in src/test
2. **Write failing tests** for the functionality
3. **Implement to make tests pass**
4. **Verify all tests pass** before marking complete

## 🏗️ Architecture Guidelines

### Database Operations:
- **Use Flyway migrations** for all schema changes (V{major}_{minor}_{patch}__{description}.sql)
- **Use DatabaseSteps.transaction** for multi-step operations
- **Use SafeSQL factory methods**: `SafeSQL.select()`, `SafeSQL.insert()`, etc.
- **Never use private constructor**: `SafeSQL("...")` is invalid

### HTML/CSS Guidelines:
- **Use CSS component classes** instead of inline styles
- **Follow CSS architecture** from CSS_ARCHITECTURE.md
- **Use design tokens**: `var(--clr-action)`, `var(--spacing-md)`, etc.
- **Mobile-first responsive design**

### Error Handling:
- **Use Result<E, S> pattern** throughout pipelines
- **Create specific failure types** per feature (e.g., CreateProjectFailures)
- **Handle ValidationFailures properly** with user-friendly messages

## 🚫 Critical Anti-Patterns (NEVER DO):

- ❌ Return JSON responses (this is an HTML application)
- ❌ Use inline styles (use CSS component classes)
- ❌ Modify existing Flyway migrations (create new ones)
- ❌ Use try-catch for business logic (use Result pattern)
- ❌ Hardcode colors (use CSS custom properties)
- ❌ Create circular dependencies between projects
- ❌ Bypass role-based access controls
- ❌ Use `SafeSQL("...")` constructor (it's private)
- ❌ Import `kotlinx.html.createHTML` (use `kotlinx.html.stream.createHTML`)

## 📋 Quick Reference Checklist

### Starting a New Feature:
1. **Read relevant documentation** from the list above
2. **Find similar existing implementation** to follow patterns
3. **Plan database changes** (new migrations if needed)
4. **Create test structure** before implementing
5. **Follow pipeline architecture** for all endpoints
6. **Use CSS component classes** for styling
7. **Test compilation** frequently during development

### Before Committing Code:
1. **Zero compilation errors** (`mvn clean compile`)
2. **All tests passing** (`mvn test`)
3. **Manual testing completed** (happy path + error cases)
4. **CSS architecture followed** (no inline styles)
5. **Documentation updated** if API contracts changed

Remember: This is a collaborative Minecraft building platform with strict permission controls and a focus on mobile-first HTML interfaces. Always prioritize user experience, data integrity, and security in implementation decisions.
