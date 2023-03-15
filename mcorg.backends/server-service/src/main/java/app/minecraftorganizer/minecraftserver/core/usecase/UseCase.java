package app.minecraftorganizer.minecraftserver.core.usecase;

public abstract class UseCase<I extends UseCase.InputValues, O extends UseCase.OutputValues> {
    public abstract O execute(I inputValues);

    public interface InputValues {}
    public interface OutputValues {}
}
