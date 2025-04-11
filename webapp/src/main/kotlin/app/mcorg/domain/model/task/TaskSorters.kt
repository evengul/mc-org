package app.mcorg.domain.model.task

class TaskSorters {
    companion object {
        fun byCompletion(a: Task, b: Task): Int {
            if (a.isDone()) {
                if (b.isDone()) {
                    return a.name.compareTo(b.name)
                }
                return 1
            } else if(b.isDone()) {
                return -1
            }
            if (a.done == b.done && a.needed == b.needed) return a.name.compareTo(b.name)
            if (a.needed == b.needed) return b.done - a.done
            return b.needed - a.needed
        }

        fun byAssignee(a: Task, b: Task): Int {
            if (a.assignee == null) {
                if (b.assignee == null) {
                    return a.name.compareTo(b.name)
                }
                return 1
            } else if(b.assignee == null) {
                return -1
            }
            return a.assignee.username.compareTo(b.assignee.username)
        }

        fun byName(a: Task, b: Task): Int {
            return a.name.compareTo(b.name)
        }

    }
}