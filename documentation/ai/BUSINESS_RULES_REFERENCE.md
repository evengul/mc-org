# MC-ORG Business Rules Reference

**Domain rules, workflows, and business constraints**

---

## 📋 Business Rule Quick Reference

| Rule                                | Domain        | Enforcement Point                | Description                                               |
|-------------------------------------|---------------|----------------------------------|-----------------------------------------------------------|
| No circular dependencies            | Projects      | `ValidateNoCyclesStep`           | Projects cannot depend on themselves transitively         |
| Only ADMIN can invite               | Worlds        | `WorldAdminPlugin`               | Only ADMIN+ can send invitations to world                 |
| One active world per user           | Users         | Business logic (soft rule)       | Users can join multiple worlds but typically focus on one |
| Demo users read-only                | Users         | `DemoUserPlugin`                 | Demo accounts cannot modify data                          |
| Ideas must have unique title        | Ideas         | Database constraint + validation | Titles must be unique per world                           |
| Projects linked to Ideas            | Projects      | `idea_id` foreign key            | Projects can optionally reference originating Idea        |
| BANNED users have no access         | Worlds        | `BannedPlugin`                   | BANNED role blocks all world operations                   |
| Task assignment requires membership | Tasks         | Validation step                  | Can only assign tasks to world members                    |
| Project dependencies must exist     | Projects      | Foreign key + validation         | Cannot depend on non-existent projects                    |
| Invitations expire                  | Invitations   | Time-based (7 days)              | Pending invites become EXPIRED after 7 days               |
| Notification persistence            | Notifications | Database + UI                    | Notifications retained until manually dismissed           |
| Stage progression is logged         | Projects      | `project_stage_changes` table    | All stage transitions are audited                         |
| Global admins bypass world rules    | Users         | `GlobalUserRole.ADMIN` check     | Superadmins can access any world                          |

---

## Executive Summary

MC-ORG is a web-based Minecraft world collaboration platform designed to centralize project management, task tracking,
and team coordination for Minecraft builders. The application solves the common problem of scattered project information
across Discord channels, spreadsheets, and in-game communication by providing a structured, role-based platform for
managing building projects from idea to completion.

## Target Users & Use Cases

### Primary User Personas

**Minecraft Builders (Core Users)**

- Team size: Typically 5-10 people per world
- Technical skill: Ranges from casual builders to advanced redstone engineers
- Usage pattern: Access while gathering resources, planning builds, and coordinating with team members
- Device preference: Mobile-first (accessing while playing), desktop for detailed planning

**World Administrators**

- Manage world settings and user permissions
- Oversee project coordination and dependencies
- Handle team invitations and role assignments

**Project Leaders**

- Create and manage individual building projects
- Break down large builds into manageable tasks
- Track progress and coordinate team efforts

### Core Problem Statement

**Current Pain Points:**

- Lost communication about project progress and completion status
- Difficulty tracking what tasks remain and who is responsible
- Scattered design information across multiple platforms
- No centralized system for managing project dependencies
- Inefficient resource planning and allocation

**Solution Approach:**
MC-ORG provides a one-stop platform for organizing Minecraft projects from idea through design, planning, resource
gathering, and building completion, with integrated team collaboration and historical progress tracking.

## Domain Model & Core Concepts

### World Structure

- **World**: A Minecraft server or save file containing multiple projects
- **Projects**: Specific buildings or systems within a world (e.g., castle, village, redstone contraption)
- **Tasks**: Granular, actionable items within projects (split into ItemTask and ActionTask)
- **Dependencies**: Cross-project relationships that define build order
- **Ideas**: Design library with templates and inspiration for projects
- **Invitations**: Access control mechanism for world membership
- **Notifications**: User alert system for important events

### Project Lifecycle

**Project Stages** (from `ProjectStage` enum):

- **Planning**: Initial design and requirement gathering
- **Design**: Detailed planning and schematic creation
- **Resource Gathering**: Collecting materials and preparing infrastructure
- **Building**: Active construction phase
- **Review**: Quality check and refinement
- **Complete**: Finished and documented
- **Archived**: Completed projects stored for reference

**Stage Transitions:**

- Manual updates by project team members
- Future consideration for automatic transitions based on task completion

### Task Management System

**Task Types** (split as of V2_20_0):

1. **ItemTask**: Collection-based tasks with item requirements
    - Track specific items and quantities (e.g., "Collect 64 stacks of stone")
    - Progress tracking with done/total counts per item
    - Support for multiple item requirements per task
    - Completed when all item requirements met

2. **ActionTask**: Completion-based activities
    - Single action to complete (e.g., "Place 32x32 stone foundation")
    - Binary completed state (done or not done)
    - Used for build steps, wiring, terraforming, etc.

**Task Properties (Common to Both Types)**:

- Granular and actionable descriptions
- Priority levels: Critical, Normal, Nice-to-have
- Assignment to specific team members
- Progress tracking and completion status
- Audit trail (created by, created at, updated at)
- No deadlines by default (unless requested by users)

### Ideas System

**Purpose**: Design library for browsing, creating, and importing build templates

**Idea Structure**:

- **Name & Description**: Clear identification and explanation
- **Category**: Farm, Contraption, Building, Decoration, Utility, Other
- **Category Data**: Dynamic JSONB field with category-specific properties
- **Author & Sub-Authors**: Credit for design contributors
- **Difficulty**: Easy, Medium, Hard, Expert
- **Rating**: Community-driven rating system (average and count)
- **Labels**: Searchable tags for organization
- **Version Range**: Minecraft versions the design works in
- **Performance Data**: Test results for contraptions/farms

**Category Schemas**:
Each category has a custom schema defining specific fields:

- **Farms**: Item type, rates per hour, dimensions, tileable
- **Contraptions**: Redstone complexity, item requirements, dimensions
- **Buildings**: Style, dimensions, materials required
- **Decorations**: Theme, scale, complexity

**Idea Workflows**:

1. **Browse Ideas**: Filter by category, difficulty, version, rating
2. **Create Idea**: Add design to library with category-specific data
3. **Import to Project**: Convert idea into project with pre-filled data
4. **Rate & Review**: Community feedback system (planned)

**Business Rules**:

- Ideas are global (not world-specific)
- Projects can reference their source idea
- Importing an idea creates a new project with idea data as template
- Category determines which custom fields are available

### Dependency Management

**Dependency Rules:**

- Projects can depend on other projects within the same world
- No cross-world dependencies allowed
- Dependency chains form tree structures with complex branching
- Cycle detection prevents circular dependencies

**Practical Examples:**

- Project A (castle) depends on Project B (stone generator) for 1 million stone
- Project B must complete before Project A can begin resource gathering phase
- Multiple projects may depend on shared infrastructure projects

## User Roles & Permissions

### World-Level Roles

**Owner (Role Level 0)**

- World creator with full administrative privileges
- Can delete worlds and manage all settings
- Cannot be transferred to other users

**Admin (Role Level 10)**

- Can invite users and assign Member/Admin roles
- Can create, edit, and delete projects
- Can manage world settings (except deletion)

**Member (Role Level 100)**

- Can create and edit projects
- Can create and complete tasks
- Can view all world content

**Banned (Role Level 1000)**

- Restricted access to world content
- Cannot perform any actions within the world

### Global System Roles

- **Developer**: System-wide administrative access
- **Support**: User assistance and moderation
- **Moderator**: Community management functions

### Access Control Principles

- All worlds are private by default
- Access granted only through invitations
- One invitation per user per world
- Role assignment occurs at invitation time
- Users can be members of multiple worlds simultaneously
- Context switching between worlds supported

## Core User Workflows

### World Management

**Creating a New World:**

1. User creates world with name, description, and Minecraft version
2. User automatically assigned Owner role
3. World becomes accessible for project creation
4. Initial project and task creation can begin

**Inviting Team Members:**

1. Admin+ role required to send invitations
2. Specify target username (must have logged in before)
3. Assign role level (Member/Admin)
4. Invitee receives in-app notification
5. Acceptance creates immediate world access

### Project Coordination

**Project Creation Workflow:**

1. Member+ creates project with name, description, and type
2. Project starts in Planning stage
3. Tasks can be added immediately
4. Dependencies configured with other projects
5. Team members can be assigned to tasks

**Dependency Planning:**

1. Identify resource or infrastructure requirements
2. Create dependency relationships between projects
3. System validates no circular dependencies exist
4. Build order determined by dependency chain
5. Progress tracking shows blocking relationships

### Task Management

**Task Creation:**

- Specific, actionable descriptions
- Choose between Countable or Action task types
- Set priority level (Critical/Normal/Nice-to-have)
- Assign to team members
- Link to parent project

**Progress Tracking:**

- Mark tasks as complete when finished
- Update project stage based on overall progress
- View dependency status and blocking projects
- Historical record of completion dates and contributors

### Notification System

**Notification Types**:

- **INVITE_RECEIVED**: New world invitation sent to user
- **INVITE_ACCEPTED**: Your invitation was accepted by user
- **INVITE_DECLINED**: Your invitation was declined by user
- **PROJECT_COMPLETED**: Project moved to complete stage
- **TASK_ASSIGNED**: Task assigned to you
- **DEPENDENCY_READY**: Blocking dependency completed (planned)
- **ROLE_CHANGED**: Your role in world changed

**Notification Workflows**:

1. **System generates notification** on specific events
2. **User receives notification** in notifications page
3. **Unread badge** shows count of unread notifications
4. **User can mark as read** individually or all at once
5. **Notifications link to related entities** (world, project, etc.)

**Business Rules**:

- Notifications are user-specific
- Read/unread state tracked per user
- Related entity includes type and ID for linking
- No email notifications yet (planned feature)
- Notifications persist even if related entity deleted

---

## 🔄 State Machine Diagrams

### Invitation State Machine

```
                    ┌─────────────┐
                    │   PENDING   │ (Initial state, invite sent)
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
  ┌──────────┐      ┌──────────┐      ┌──────────┐
  │ ACCEPTED │      │ REJECTED │      │ EXPIRED  │
  └────┬─────┘      └──────────┘      └──────────┘
       │             (User declined)    (After 7 days)
       ▼
  Create world_members entry
  Send notification to inviter
  User gains access to world
```

**State Transitions:**

- `PENDING → ACCEPTED`: User accepts invite → Create membership
- `PENDING → REJECTED`: User declines invite → Send rejection notification
- `PENDING → EXPIRED`: Time-based (7 days pass) → Invite no longer valid
- No transitions FROM terminal states (ACCEPTED, REJECTED, EXPIRED)

### Project Stage State Machine

```
┌──────────┐     ┌──────────┐     ┌────────────┐     ┌──────────┐     ┌───────────┐
│ PLANNING ├────►│ BUILDING ├────►│ DECORATING ├────►│ COMPLETE ├────►│ ABANDONED │
└──────────┘     └──────────┘     └────────────┘     └──────────┘     └───────────┘
     │                 │                                     │
     └─────────────────┴─────────────────────────────────────┘
                   (Can move to ABANDONED from any state)
```

**Stage Progression Rules:**

- Typically flows: PLANNING → BUILDING → DECORATING → COMPLETE
- Can skip stages (e.g., PLANNING → COMPLETE for simple projects)
- Can move backwards (e.g., BUILDING → PLANNING if redesign needed)
- ABANDONED is terminal state (represents cancelled project)
- All transitions logged in `project_stage_changes` table

### Task State Machine (ActionTask)

```
┌────────────────┐
│  NOT COMPLETED │ (Initial state, completed = false)
└───────┬────────┘
        │
        ▼
┌────────────────┐
│   COMPLETED    │ (Terminal state, completed = true)
└────────────────┘
```

**Note**: ItemTasks use quantity tracking instead of boolean completion

---

## ❓ Common "What If" Scenarios

### "What if a user is removed from a world?"

**Trigger**: Admin removes world membership (future feature)

**Cascade Effects**:

- Membership record deleted from `world_members`
- User loses access to world immediately
- Task assignments to user removed
- Projects created by user remain (preserve history)
- Audit trail preserved (`created_by` field unchanged)

**Notifications**:

- Removed user receives `ROLE_CHANGED` or `REMOVED_FROM_WORLD` notification
- No notifications sent to other members

**Data Integrity**:

- Foreign key constraints maintain referential integrity
- Historical data (projects, tasks created) preserved for audit trail

---

### "What if a project is deleted?"

**Trigger**: Admin/owner deletes project

**Cascade Effects**:

- All item_tasks for project deleted (`ON DELETE CASCADE`)
- All action_tasks for project deleted (`ON DELETE CASCADE`)
- All project_dependencies records deleted (both as dependent and dependency)
- All project_locations deleted
- All project_stage_changes deleted
- Project link from Ideas remains (if applicable)

**Notifications**:

- All users with assigned tasks receive notification (future)
- Project creator receives confirmation notification

**Business Rules**:

- Cannot undelete (no soft delete currently)
- Dependencies on this project are removed from other projects
- Other projects that depended on this project are notified (future)

---

### "What if two projects depend on each other (circular dependency)?"

**Prevention**: System validates dependency graph before allowing creation

**Validation Logic**:

```
Project A wants to depend on Project B
→ Check if B already depends on A (directly or transitively)
→ If yes: Reject with ValidationError
→ If no: Allow dependency creation
```

**Detection**: Recursive CTE query or graph traversal in `ValidateNoCyclesStep`

**User Experience**:

- Form submission rejected with clear error message
- Suggests removing existing dependency or creating different structure
- UI could show dependency tree to help visualize

---

### "What if an invitation is sent to a user who is already a member?"

**Current Behavior**: Not explicitly handled (should be prevented)

**Recommended Logic**:

1. Check if user already has membership in world
2. If yes: Show error "User is already a member"
3. If no: Allow invitation creation

**Edge Cases**:

- User has BANNED role: Allow re-invite to restore access
- User has different role: Show option to change role instead

---

### "What if a project's dependency completes?"

**Trigger**: Dependency project moves to COMPLETE stage

**Notifications** (Future Feature):

- All projects depending on completed project receive `DEPENDENCY_READY` notification
- Assigned users on dependent projects notified
- Project owners of dependent projects notified

**UI Updates**:

- Dependency badge changes from "Blocked" to "Ready"
- Project can now move forward in build order
- Progress tracking updated

---

### "What if a user tries to access a world they're not a member of?"

**Plugin Chain**:

1. `AuthPlugin` validates JWT token → User authenticated
2. `WorldParamPlugin` extracts worldId from URL
3. `WorldMemberPlugin` checks `world_members` table
4. If no membership found → Return 403 Forbidden
5. If membership exists → Continue to handler

**Exception**: Global admins (`GlobalUserRole.ADMIN`) bypass this check

**Response**: Error page with "You don't have access to this world"

---

### "What if an Idea is deleted that has linked projects?"

**Current Behavior**: `idea_id` in projects is nullable with `ON DELETE SET NULL`

**Cascade Effects**:

- All projects with `idea_id = <deleted_idea_id>` → Set to NULL
- Projects remain intact, just lose reference to originating Idea
- No data loss for projects

**Alternative Considered**: Prevent deletion if projects linked (rejected for flexibility)

---

## Integration Requirements

### Current State

- Standalone web application
- No external integrations implemented
- In-app notifications only (no email system yet)

### Planned Integrations

- **Discord Integration**: Replace some project management features currently done in Discord
- **Email Notifications**: For important events and updates
- **Mobile App**: Native mobile experience for on-the-go access

## Technical Requirements

### Performance Expectations

- Support 100+ concurrent users
- Multiple worlds and projects per user
- Responsive mobile-first design
- Fast page load times for mobile usage

### Device Optimization

- **Primary**: Mobile devices (used while playing Minecraft)
- **Secondary**: Desktop computers (detailed planning and administration)
- **Considerations**: Tablet support for viewing while building

### Accessibility

- Screen reader compatibility
- Keyboard navigation support
- Future consideration for high contrast themes and font size preferences

## Success Metrics

### Primary Goals

1. **Centralized Project Organization**: Replace scattered Discord/spreadsheet workflows
2. **Improved Team Collaboration**: Clear task assignment and progress visibility
3. **Efficient Resource Planning**: Better coordination of material gathering and infrastructure
4. **Historical Progress Tracking**: Complete build documentation and timeline

### Measurement Criteria

- User engagement and retention rates
- Project completion rates compared to traditional methods
- Reduction in lost communication incidents
- Team coordination efficiency improvements

## Future Enhancements

### Short-term Considerations

- Email notification system implementation
- Enhanced mobile experience
- Discord bot integration
- Project templates for common build types

### Long-term Vision

- Public showcase worlds for completed builds
- Integration with Minecraft server plugins
- Advanced dependency visualization
- Community project sharing and collaboration

## Business Rules Summary

1. **World Access**: Private by default, invitation-only access
2. **Role Hierarchy**: Owner > Admin > Member > Banned (numeric levels: 0, 10, 100, 1000)
3. **Project Dependencies**: No circular dependencies, world-scoped only
4. **Task Management**: Granular, actionable, priority-based
5. **Data Integrity**: Complete audit trail with creation/update timestamps
6. **Scalability**: Support for multiple concurrent worlds and projects per user

---

## Additional Resources

- **[AI_QUICKSTART_GUIDE.md](AI_QUICKSTART_GUIDE.md)** - Quick orientation
- **[ARCHITECTURE_REFERENCE.md](ARCHITECTURE_REFERENCE.md)** - System architecture
- **[DEVELOPMENT_GUIDE.md](DEVELOPMENT_GUIDE.md)** - Implementation patterns
- **[CSS_ARCHITECTURE.md](CSS_ARCHITECTURE.md)** - Styling guide
- **[PROJECT_STATUS.md](../project_status/PROJECT_STATUS.md)** - Feature status

---

**Document Version**: 1.0  
**Last Updated**: January 12, 2026  
**Maintained By**: Development Team

This functional specification provides comprehensive guidance for development teams and AI agents to understand the
business purpose, user workflows, and technical requirements necessary to build and maintain the MC-ORG platform
effectively.