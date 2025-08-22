# MC-ORG Functional Specification

## Executive Summary

MC-ORG is a web-based Minecraft world collaboration platform designed to centralize project management, task tracking, and team coordination for Minecraft builders. The application solves the common problem of scattered project information across Discord channels, spreadsheets, and in-game communication by providing a structured, role-based platform for managing building projects from conception to completion.

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
MC-ORG provides a one-stop platform for organizing Minecraft projects from idea through design, planning, resource gathering, and building completion, with integrated team collaboration and historical progress tracking.

## Domain Model & Core Concepts

### World Structure
- **World**: A Minecraft server or save file containing multiple projects
- **Projects**: Specific buildings or systems within a world (e.g., castle, village, redstone contraption)
- **Tasks**: Granular, actionable items within projects
- **Dependencies**: Cross-project relationships that define build order

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

**Task Types:**
1. **Countable Tasks**: Specific quantity collection (e.g., "Collect 64 stacks of stone")
2. **Action Tasks**: Completion-based activities (e.g., "Complete foundation layer", "Place 32x32 stone foundation")

**Task Properties:**
- Granular and actionable descriptions
- Priority levels: Critical, Normal, Nice-to-have
- Assignment to specific team members
- Progress tracking and completion status
- No deadlines by default (unless requested by users)

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

This functional specification provides comprehensive guidance for development teams and AI agents to understand the business purpose, user workflows, and technical requirements necessary to build and maintain the MC-ORG platform effectively.