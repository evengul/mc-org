window.addEventListener('load', () => {
    const menuButton = document.getElementById("menu-button")
    const menu = document.getElementById("menu-links")

    menuButton.addEventListener("click", () => {
        if (menu.classList.contains("invisible")) {
            menu.classList.remove("invisible")
            menu.classList.add("visible")
        } else {
            menu.classList.remove("visible")
            menu.classList.add("invisible")
        }
    })
})



