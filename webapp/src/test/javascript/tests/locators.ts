import {Page} from "@playwright/test";

export const locators = (page: Page) => ({
  SIGN_IN: {
    title: page.getByText("Welcome to MCORG!"),
    button: page.getByText("Sign in with Microsoft")
  },
  menuButton: page.locator("#menu-button"),
  WORLDS: {
    title: page.getByRole("heading", {level: 1, name: "Worlds"}),
    CREATE_WORLD: {
      name: page.locator("#add-world-name-input"),
      submitButton: page.locator("#add-world-submit-button")
    }
  },
  PROJECTS: {
    title: page.getByRole("heading", {name: "Projects"}),
    showCreateProjectDialogButton: page.locator("#show-create-project-dialog-button"),
    amountShowed: page.locator("#project-filter-amount"),
    EMPTY_STATE: {
      self: page.locator("#empty-project-state"),
      text: page.getByText("Welcome to your new world! Start by creating your first project."),
      createButton: page.getByText("Create Project")
    },
    FILTER: {
      self: page.locator("#projects-filter"),
      search: page.locator("#projects-search-input"),
      hideCompleted: page.locator("#projects-hide-completed-checkbox"),
      clearButton: page.locator("#projects-filter-clear-button"),
      submitButton: page.getByRole("button").getByText("Search"),
    },
    CREATE_DIALOG: {
      self: page.locator("#add-project-dialog"),
      name: page.locator("#project-add-name-input"),
      dimension: page.locator("#project-add-dimension-input"),
      priority: page.locator("#project-add-priority-input"),
      requiresPerimeter: page.locator("#project-add-requires-perimeter-input"),
      cancelButton: page.locator("#project-add-cancel-button"),
      submitButton: page.locator("#project-add-submit-button"),
    },
    project: (name: string) => {
      const project = page.locator(".project", {has: page.getByRole("heading", {level: 2, name: name})})
      return {
        self: project,
        header: project.getByText(name),
        deleteButton: project.locator(".delete-project-button"),
        priority: project.locator("p:nth-of-type(1)"),
        dimension: project.locator("p:nth-of-type(2)"),
        assignment: project.locator(".project-assignment").locator("select"),
        progress: project.locator(".project-progress")
      }
    }
  },
  PROJECT: {
    taskBoard: page.locator("#task-board"),
    DOABLE: {
      board: page.locator("#doable-tasks"),
      header: page.getByText("Doable Tasks"),
      columns: page.locator("#doable-tasks-columns"),
      hideDoableTasksButton: page.locator("#doable-tasks").locator(".toggle-button"),
      createDoableButton: page.locator("#add-doable-task-button"),
      todoColumn: page.locator("#task-list-doable-todo"),
      inProgressColumn: page.locator("#task-list-doable-in-progress"),
      doneColumn: page.locator("#task-list-doable-done"),
      ADD_DOABLE: {
        nameField: page.locator("#add-doable-task-name-input"),
        submitButton: page.locator("#add-doable-task-submit-button"),
      },
      task: (name: string) => {
        const task = page.locator(".task", {
          hasText: name
        })

        return {
          self: task,
          deleteButton: task.locator(".delete-task-button"),
          assign: task.locator(".assign-select")
        }
      }
    },
    COUNTABLE: {
      board: page.locator("#countable-tasks"),
      header: page.getByText("Countable Tasks"),
      hideCountableTasksButton: page.locator("#countable-tasks").locator(".toggle-button"),
      createCountableButton: page.locator("#add-countable-task-button"),
      columns: page.locator("#countable-tasks-columns"),
      todoColumn: page.locator("#task-list-countable-todo"),
      inProgressColumn: page.locator("#task-list-countable-in-progress"),
      doneColumn: page.locator("#task-list-countable-done"),
      ADD_COUNTABLE: {
        nameField: page.locator("#add-countable-task-name-input"),
        amountField: page.locator("#add-countable-task-amount-input"),
        submitButton: page.locator("#add-countable-task-submit-button"),
      },
      task: (name: string) => {
        const task = page.locator(".task", {
          hasText: name
        })

        return {
          self: task,
          deleteButton: task.locator(".delete-task-button"),
          amount: task.locator("p.task-progress"),
          assign: task.locator(".assign-select")
        }
      }
    }
  }
})