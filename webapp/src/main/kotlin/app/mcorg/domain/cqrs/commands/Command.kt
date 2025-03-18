package app.mcorg.domain.cqrs.commands

import app.mcorg.domain.cqrs.Failure
import app.mcorg.domain.cqrs.Input
import app.mcorg.domain.cqrs.Output
import app.mcorg.domain.cqrs.Success

interface Command<in I : Input, S : Success, E : Failure> {
    fun execute(input: I): Output<E, S>
}