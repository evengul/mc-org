function toggleVisibility(id, button) {
    const element = document.getElementById(id);
    if (element.classList.contains("collapsed")) {
        element.classList.remove("collapsed")
        button.querySelector('.caret').classList.remove('rotated');
    } else {
        element.classList.add("collapsed")
        button.querySelector('.caret').classList.add('rotated');
    }
}