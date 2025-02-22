package app.mcorg.domain.users

fun sortUsersBySelectedOrName(users: List<User>, currentUser: User, selectedUser: User?): List<User> {
    fun sorter(a: User, b: User): Int {
        if (selectedUser != null) {
            if (a.id == selectedUser.id) return -1
            if (b.id == selectedUser.id) return 1
        }
        if (a.id == currentUser.id) return -1
        if (b.id == currentUser.id) return 1
        return b.username.compareTo(a.username)
    }

    return users.sortedWith(::sorter)
}