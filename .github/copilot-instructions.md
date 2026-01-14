# GitHub Copilot Instructions for MC-ORG Project

## 🎯 Project Overview

MC-ORG is a **Ktor-based Kotlin web application** using **server-side HTML generation** with Kotlin HTML DSL. This is a Minecraft world collaboration platform for managing building projects, tasks, and team coordination.

**CRITICAL**: This application serves HTML responses (not JSON APIs) and uses **HTMX for dynamic interactions**. PUT/PATCH/POST/DELETE endpoints return HTML fragments that replace specific page sections.

## 📚 Documentation Structure

**ALWAYS START HERE**: **[AI_QUICKSTART_GUIDE.md](../documentation/ai/AI_QUICKSTART_GUIDE.md)**
- 10-minute orientation with core concepts, patterns, and navigation
- Contains quick reference cheat sheets and common patterns
- "I need to..." decision tree for finding the right documentation

### Core Documentation

1. **[ARCHITECTURE_REFERENCE.md](../documentation/ai/ARCHITECTURE_REFERENCE.md)** - System architecture and domain model
2. **[DEVELOPMENT_GUIDE.md](../documentation/ai/DEVELOPMENT_GUIDE.md)** - Implementation patterns and best practices
3. **[BUSINESS_RULES_REFERENCE.md](../documentation/ai/BUSINESS_RULES_REFERENCE.md)** - Domain rules and workflows
4. **[CSS_ARCHITECTURE.md](../documentation/ai/CSS_ARCHITECTURE.md)** - Styling patterns
5. **[PROJECT_STATUS.md](../documentation/project_status/PROJECT_STATUS.md)** - Feature status and sprints

## 🚨 Critical Protocols

### Before Writing ANY Code:
1. **Read AI_QUICKSTART_GUIDE.md** if you haven't already
2. **Run `mvn clean compile`** - must pass with zero errors
3. **Find similar implementation** to follow established patterns
4. **Create tests first** - test-driven development

### Before Committing:
1. **`mvn clean compile`** - zero errors
2. **`mvn test`** - all tests pass
3. **Manual testing** - happy path + error cases
4. **Follow patterns** - as documented in guides

## 🔑 Key Reminders

**Authorization**: Handled by Ktor plugins (NOT in pipelines) - see RolePlugins.kt
**Pipeline Execution**: Can be sequential or parallel - see DEVELOPMENT_GUIDE.md
**Database**: Use DatabaseSteps with SafeSQL factory methods
**HTML**: Server-side only, use HTMX for dynamic updates
**CSS**: Component classes, not inline styles

## 🚫 Critical Anti-Patterns

- ❌ Return JSON (this is HTML)
- ❌ Check authorization in pipelines (use plugins)
- ❌ Use `SafeSQL("...")` constructor (use factory methods)
- ❌ Import `kotlinx.html.createHTML` (use `.stream.createHTML`)
- ❌ Inline styles (use CSS classes)

## 📖 More Information

All detailed patterns, examples, and guidelines are in the documentation files listed above. The AI_QUICKSTART_GUIDE.md contains navigation tables to help you find specific information quickly.

Remember: This is a collaborative Minecraft building platform with strict permission controls and mobile-first HTML interfaces. Always prioritize user experience, data integrity, and security.
