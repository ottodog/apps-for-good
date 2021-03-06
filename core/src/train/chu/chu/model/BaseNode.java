package train.chu.chu.model;

import java.io.Serializable;

/**
 * Created by Da-Jin on 6/5/2016.
 * Previously referred to vaguely as Block
 * Node models one element of the sandbox
 * eg. operators, operands, fractions
 */
public class BaseNode implements Node, Serializable {
    private String data;
    ExpressionNode expression;
    boolean selected = false;

    protected BaseNode(String data, ExpressionNode expression) {
        this(data, expression, expression.getChildren().size());
    }
    protected BaseNode(String data, ExpressionNode expression, int index) {
        this.data = data;
        this.expression = expression;
        expression.getChildren().add(index, this);
    }
    protected void setSelected(boolean selected){
        this.selected = selected;
    }

    @Override
    public void move(BaseNode to, Side side){
        if(to==this)return;
        this.remove();
        int toIndex = to.getExpression().getChildren().indexOf(to)+side.getOffset();
        to.getExpression().getChildren().add(toIndex,this);
        this.expression = to.getExpression();
        Model.INSTANCE.getInsertionPoint().move(this,Side.RIGHT);
    }

    @Override
    public void moveInto(BlankNode into) {
        this.remove();
        int toIndex = into.getExpression().getChildren().indexOf(into);
        into.getExpression().getChildren().add(toIndex,this);
        this.expression = into.getExpression();
        into.remove();
        Model.INSTANCE.update();
    }

    @Override
    public void remove(){
        expression.getChildren().remove(this);
        Model.INSTANCE.update();
    }

    public boolean isSelected(){
        return selected;
    }

    @Override
    public ExpressionNode getExpression() {
        return expression;
    }

    public String getData() {
        return data;
    }
}
