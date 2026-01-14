# AI Documentation for MC-ORG

**Complete documentation for AI agents working on the MC-ORG project**

---

## 📚 Documentation Structure

This directory contains comprehensive documentation designed for AI agents (like GitHub Copilot) to understand,
maintain, and extend the MC-ORG codebase.

### Core Documentation Files

#### 1. **[AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)** ⭐ START HERE

- **Purpose**: 10-minute orientation for new AI agents
- **Contains**: Project overview, core concepts, critical patterns, navigation guide
- **When to read**: ALWAYS start here before any work
- **Key features**:
    - Quick reference cheat sheets
    - Common patterns and imports
    - "I need to..." navigation table
    - Critical "never do this" list

#### 2. **[ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)**

- **Purpose**: Complete system architecture and domain model
- **Contains**: Technology stack, domain entities, database schema, routing, authentication
- **When to read**: Understanding system structure, domain model, or data flow
- **Key features**:
    - Detailed entity relationship diagrams
    - Complete domain model with all properties
    - Database schema (V2_21_0+)
    - Permission model and authorization flows

#### 3. **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)**

- **Purpose**: Implementation patterns and best practices
- **Contains**: Pipeline architecture, Step patterns, HTMX integration, database operations, testing
- **When to read**: Implementing any feature, endpoint, or code modification
- **Key features**:
    - Complete pipeline pattern examples
    - AppFailure error hierarchy documentation
    - HTMX helper functions and patterns
    - SafeSQL and DatabaseSteps usage
    - Validation and testing patterns

#### 4. **[BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)**

- **Purpose**: Domain rules, workflows, and business constraints
- **Contains**: User roles, project lifecycle, dependency rules, Ideas system, notifications
- **When to read**: Understanding business logic, user workflows, or permission requirements
- **Key features**:
    - Complete permission hierarchy
    - Invitation workflows
    - Ideas system documentation
    - Task management rules (ItemTask/ActionTask)

#### 5. **[CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)**

- **Purpose**: CSS component system and styling patterns
- **Contains**: Component classes, utility patterns, design tokens, refactoring guidelines
- **When to read**: Modifying HTML templates or adding styles
- **Key features**:
    - Complete design token reference (--clr-*, --spacing-*)
    - Component class library
    - Responsive design patterns
    - HTML refactoring checklist

---

## 🗺️ Quick Navigation

**"I need to..."** → **"Read this..."**

| Goal                   | Primary Document                                                           | Section                        |
|------------------------|----------------------------------------------------------------------------|--------------------------------|
| Get started quickly    | [AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)                           | Full document                  |
| Understand the system  | [ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)                     | Technology Stack, Domain Model |
| Implement an endpoint  | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)                               | Pipeline Architecture          |
| Handle errors          | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)                               | Error Handling                 |
| Use HTMX               | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)                               | HTMX Integration               |
| Query database         | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)                               | Database Operations            |
| Validate input         | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)                               | Validation Patterns            |
| Write tests            | [DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)                               | Testing Requirements           |
| Understand permissions | [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)                 | Permission Model               |
| Learn business rules   | [BUSINESS_RULES_REFERENCE.md](BUSINESS_RULES_REFERENCE.md)                 | Core User Workflows            |
| Style HTML             | [CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)                                 | Component Classes              |
| Check feature status   | [../project_status/PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md) | Completed/Pending Features     |

---

## 📊 Documentation Version

**Current Version**: 2.0  
**Restructured**: January 12, 2026  
**Previous Version**: 1.0 (deprecated files kept for transition)

### What Changed in 2.0?

**Consolidation**:

- Merged overlapping content from 6 files into 4 focused documents
- Eliminated ~40% redundancy
- Created clear separation of concerns (what/why/how)

**New Content**:

- Added AI_QUICKSTART_GUIDE.md for quick orientation
- Documented Ideas system (previously missing)
- Documented AppFailure error hierarchy
- Documented HTMX helper functions
- Updated Task model (split into ItemTask/ActionTask)
- Added notification workflows

**Improved Organization**:

- Clear "start here" guide
- Navigation tables for quick reference
- Consistent structure across all documents
- Cross-references between related sections

**Better Accuracy**:

- Updated to reflect current codebase (49+ migrations)
- All code examples verified and current
- Removed outdated information
- Added missing features from recent development

---

## 🗂️ Deprecated Files (Transition Period)

The following files have been superseded but are kept temporarily for reference:

- **AI_AGENT_DOCUMENTATION.md** → Replaced by ARCHITECTURE_REFERENCE.md
- **AI_INTEGRATION_SPECS.md** → Replaced by DEVELOPMENT_GUIDE.md
- **API_SPECIFICATIONS.md** → Replaced by DEVELOPMENT_GUIDE.md
- **IMPLEMENTATION_PLAN.md** → Moved to ../project_status/PROJECT_STATUS.md

**These files will be removed in a future update.** All new content should reference the new documentation structure.

---

## 🎯 Using This Documentation

### For AI Agents

1. **Always start with** [AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)
2. **Use navigation tables** to find specific information
3. **Follow code patterns** exactly as documented
4. **Check deprecation notices** before using old files
5. **Verify code examples** compile before using

### For Human Developers

1. Read the quickstart guide to understand AI agent expectations
2. Use the same documentation when working with AI agents
3. Keep documentation updated as code evolves
4. Add deprecation notices when restructuring

### Maintenance Guidelines

- **Update frequency**: After major features or architectural changes
- **Code examples**: Must compile and match current patterns
- **Cross-references**: Keep links between documents accurate
- **Version tracking**: Update version number and date when changing
- **Deprecation**: Use deprecation notices before removing content

---

## 📞 Additional Resources

- **[PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)** - Current project status and implementation tracking
- **[copilot-instructions.md](../../.github/copilot-instructions.md)** - GitHub Copilot configuration
- **[Root README.md](../../README.md)** - Project overview and setup instructions

---

## 🤝 Contributing

When updating AI documentation:

1. **Maintain consistency** across all files
2. **Test code examples** to ensure they compile
3. **Update cross-references** when moving content
4. **Version the changes** (update date and version number)
5. **Add deprecation notices** before removing old content
6. **Keep examples current** with actual codebase patterns

---

**Last Updated**: January 13, 2026  
**Documentation Version**: 2.1  
**Maintained By**: Development Team
