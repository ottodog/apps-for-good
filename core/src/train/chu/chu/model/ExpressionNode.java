package train.chu.chu.model;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.StringBuilder;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * Created by Da-Jin on 6/5/2016.
 * One level below root in expression tree
 * Calculates final result
 * Stores its position in sandbox world
 */
public class ExpressionNode implements Positioned {

    private final Model model;
    private float x;
    private float y;
    private Array<BaseNode> children;

    protected ExpressionNode(float x, float y, Model model) {
        this.x = x;
        this.y = y;
        this.model = model;
        this.children = new Array<>();
    }

    public void move(float x, float y){
        this.x=x;
        this.y=y;
        model.update();
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public void setX(float x) {
        this.x = x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public void setY(float y) {
        this.y = y;
    }

    public Array<BaseNode> getChildren() {
        return children;
    }

    public String getResult(){
        StringBuilder sb = new StringBuilder();
        for(BaseNode child : getChildren()){
            sb.append(child.getData());
        }
        try {
            Expression expression = new ExpressionBuilder(sb.toString()).build();
            if (expression.validate().isValid()) {
                return String.valueOf(expression.evaluate());
            }
        }catch(Exception e){}
        return "false";
    }
}
