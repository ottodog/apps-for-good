package train.chu.chu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.DelaunayTriangulator;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Polygon;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import train.chu.chu.model.BaseNode;
import train.chu.chu.model.ExpressionNode;
import train.chu.chu.model.InsertionPoint;
import train.chu.chu.model.Model;
import train.chu.chu.model.ModelListener;

public class Main implements Screen, ModelListener{
    private static final float TOOLBAR_HEIGHT = 56*Gdx.graphics.getDensity();//Following material spec, toolbar is 56 density independent pixels
    public static AnalyticsProvider analytics;
    private Stage stage;
    public static Skin skin;
    private Expression row;
    private Label result;
    private Table rootTable;
    private Image redo;
    private Image undo;
    private Image parenthesize;
    private Image addExpression;
    private Image backSpace;
    private Image keyPadToggle;
    private Table keypad;
    private Table keyPadTabs;
    private int tabNum;
    private int keyToggle;
    private int size;
    private TrashCan trashCan = null;
    private Table toolbar;

    public static DragAndDrop dragAndDrop = new DragAndDrop();
    private Label debug;
    public static WidgetGroup calcZone;

    private ArrayList<Float[]> circle = new ArrayList<>();
    private Float[] p1, p2;
    private short[] triangleIndices;
    private float[] boundVertices;
    private Polygon wholebound;
    private Array<Polygon> bounds;
    private Texture logo;
    private int prevtabNum;
    private Model model;
    private boolean landscape;

    private static final int GRAPHWIDTH = 50;
    private static final float dragSquareSize = 20;

    private Table toolbarLeft;

    private HashMap<BaseNode, LabelBlock> blockMap = new HashMap<>();
    private HashMap<ExpressionNode, Expression> expressionMap = new HashMap<>();
    private SelectedBlock selectedBlock;

    public Main(AnalyticsProvider analytics, Skin skin) {
        this.analytics = analytics;
        this.skin = skin;
    }

    @Override
    public void show() {
        Model.INSTANCE.setListener(this);
        this.model = Model.INSTANCE;
        selectedBlock = new SelectedBlock(model);

        dragAndDrop.setTapSquareSize(dragSquareSize);

        //Instantiate Stage for scene2d management
        stage = new Stage();
        Gdx.input.setInputProcessor(stage);

        //Add a root table.
        rootTable = new Table();
        rootTable.setFillParent(true);
        stage.addActor(rootTable);

        calcZone = new WidgetGroup(){
            public int thickness;
            public Vector2 min, max;

            @Override
            public void draw(Batch batch, float parentAlpha) {
                min = ScaleUtils.positionWithin(this,0,0);
                max = ScaleUtils.positionWithin(this,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());

                thickness = (int) (Math.ceil(2/getScaleX()));
                batch.setColor(Color.valueOf("29B6F6"));
                applyTransform(batch, computeTransform());
                for(int i = (int) (min.x/GRAPHWIDTH)*GRAPHWIDTH; i < max.x; i+=GRAPHWIDTH) {
                    BatchShapeUtils.drawLine(batch, i, min.y, i, max.y, thickness);
                }
                for(int i = (int) (min.y/GRAPHWIDTH)*GRAPHWIDTH; i < max.y; i+=GRAPHWIDTH) {
                    BatchShapeUtils.drawLine(batch, min.x, i, max.x, i, thickness);
                }
                resetTransform(batch);
                super.draw(batch, parentAlpha);
            }
        };
        calcZone.setFillParent(true);
        calcZone.setTouchable(Touchable.childrenOnly);

        stage.addListener(new ActorGestureResizer(stage, calcZone, new Vector2(1000, 1000)));
        stage.addActor(calcZone);

        stage.addListener(new ClickListener() {

            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                //track touch down location.. TODO maybe change this to also track time?

                Actor hit = stage.hit(x, y, true);
                boolean hitNothing = hit == null;
                boolean hitUnselectedBlock = hit instanceof LabelBlock && !((LabelBlock)hit).getNode().isSelected();
                boolean hitKeypad = hit instanceof KeypadButton;
                if(hitNothing || hitUnselectedBlock || hitKeypad){
                    model.deselect();
                }
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
            }
        });
        stage.addListener(new ActorGestureListener() {
            boolean drawing = false;

            @Override
            public void touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (stage.hit(x, y, true) instanceof LabelBlock) {
                    return;
                }
                drawing = true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {//TODO
                if (drawing && circle.size() > 9) {
                    Bench.start("touchup");
                    Float[] first = circle.get(0);
                    Float[] last = circle.get(circle.size() - 1);
                    //Check if they've come close enough to closing the polygon
                    if (Vector2.dst2(first[0], first[1], last[0], last[1]) < 40000) {
                        //Convert the arraylist of 2 element float[] into a single float[] to be compatible with Polygon
                        boundVertices = new float[circle.size() / 3 * 2];
                        for (int i = 0; i < circle.size() / 3; i++) {
                            Float[] point = circle.get(i * 3);
                            boundVertices[2 * i] = point[0];
                            boundVertices[2 * i + 1] = point[1];
                        }
                        triangleIndices = new DelaunayTriangulator().computeTriangles(boundVertices, false).toArray();
                        float[] trianglefloats = new float[triangleIndices.length * 2];
                        for (int i = 0; i < triangleIndices.length; i++) {
                            trianglefloats[2 * i] = boundVertices[2 * triangleIndices[i]];
                            trianglefloats[2 * i + 1] = boundVertices[2 * triangleIndices[i] + 1];
                        }
                        wholebound = new Polygon(boundVertices);
                        bounds = new Array<Polygon>();
                        for (int i = 0; i < trianglefloats.length; i += 6) {
                            bounds.add(new Polygon(Arrays.copyOfRange(trianglefloats, i, i + 6)));
                        }
                        Polygon blockPoly = new Polygon(), overlap = new Polygon();
                        HashMap<LabelBlock, Float> overlaps = new HashMap<>();

                        Vector2 v1, v2, v3, v4, tmpA = new Vector2(0, 0), tmpB = new Vector2(), tmpC = new Vector2(), tmpD = new Vector2();
                        float area;
                        //Put the overlap areas into a hash map, associating area with blocks
                        Bench.start("intersect");
                        for (Actor actor : blockMap.values()) {
                            if (actor instanceof LabelBlock) {//We know they will be blocks, but make sure
                                v1 = actor.localToStageCoordinates(tmpA.set(0, 0));
                                v2 = actor.localToStageCoordinates(tmpB.set(actor.getWidth(), 0));
                                v3 = actor.localToStageCoordinates(tmpC.set(actor.getWidth(), actor.getHeight()));
                                v4 = actor.localToStageCoordinates(tmpD.set(0, actor.getHeight()));
                                blockPoly.setVertices(new float[]{v1.x, v1.y, v2.x, v2.y, v3.x, v3.y, v4.x, v4.y});
                                area = 0;
                                for (Polygon p : bounds) {
                                    try {
                                        if (Intersector.intersectPolygons(blockPoly, p, overlap)) {
                                            area += overlap.area();
                                        }
                                    } catch (IllegalArgumentException e) {}
                                }
                                if (area > blockPoly.area() * .4f) {
                                    overlaps.put((LabelBlock) actor, area);
                                }
                            }
                        }
                        Bench.end("intersect");
                        Array<BaseNode> selection = new Array<>();
                        for(LabelBlock block : overlaps.keySet()){
                            selection.add(block.getNode());
                        }
                        model.selectBlocks(selection);
                    }
                    Bench.end("touchup");
                }
                drawing = false;
                circle.clear();

            }

            @Override
            public void pan(InputEvent event, float x, float y, float deltaX, float deltaY) {
                if (!drawing) return;
                circle.add(new Float[]{x, y});
            }
        });


        //Creates the trash can
        trashCan = new TrashCan();
        trashCan.setDrawable(skin, "delete");

        trashCan.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float z, float y) {
                model.getExpressions().clear();
                model.addToHistory();
                syncWithModel();
            }
        });


        //Creates backspace button
        backSpace= new Image(skin.getDrawable("backspace"));
        backSpace.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float z, float y) {
                model.backspace();
                model.addToHistory();
            }
        });

        //Creates the redo button
        redo = new Image(skin.getDrawable("redo"));
        redo.setScaling(Scaling.fit);
        redo.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float z, float y) {
                model.redo();
            }
        });

        //creates the undo button
        undo = new Image(skin.getDrawable("undo"));
        undo.setScaling(Scaling.fit);
        undo.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float z, float y) {
                model.undo();
            }
        });

        addExpression=new Image(skin.getDrawable("newExp"));
        addExpression.setScaling(Scaling.fit);
        addExpression.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float z, float y) {
                model.addExpression(50, 50);
            }
        });
        //Key Pad toggle button

        keyPadToggle = new Image(skin.getDrawable("downArrow"));
        keyPadToggle.setScaling(Scaling.fit);
        //Toggle the keypad on and off
        keyPadToggle.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float z, float y) {

                if (keyToggle == 0) {
                    //Hide KeyPad, set to up arrow
                    keyToggle = 1;
                    keyPadTabs.addAction(Actions.moveTo(keyPadTabs.getX(), -100, 0.5f, Interpolation.swingIn));
                    keypad.addAction(Actions.moveTo(keypad.getX(), -(size * 4 + 100), 0.5f, Interpolation.swingIn));
                    keyPadToggle.setDrawable(skin.getDrawable("upArrow"));


                } else {
                    //Bring up keypad, set to down arrow
                    keyToggle = 0;
                    keyPadTabs.addAction(Actions.moveTo(keyPadTabs.getX(), (size * 4)+2, 0.5f, Interpolation.swingOut));
                    keypad.addAction(Actions.moveTo(keypad.getX(), 0, 0.5f, Interpolation.swingOut));
                    keyPadToggle.setDrawable(skin.getDrawable("downArrow"));
                }
            }
        });


        parenthesize= new Image(skin.getDrawable("parenthesis"));
        parenthesize.setScaling(Scaling.fit);
        parenthesize.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y) {
                model.parenthesizeSelected();
                model.addToHistory();
            }
        });

        //Left side of toolbar
        toolbarLeft=new Table();
        toolbarLeft.defaults().size(TOOLBAR_HEIGHT);
        toolbarLeft.add(trashCan);
        toolbarLeft.add(backSpace);

        //Create the toolbar, keypad toggle, undo/redo buttons
        toolbar = new Table();
        toolbar.defaults().size(TOOLBAR_HEIGHT);
        toolbar.add(parenthesize);
        toolbar.add(addExpression);
        toolbar.add(keyPadToggle);
        toolbar.add(undo);
        toolbar.add(redo);

        //Debugger
        debug = new Label("", skin);
        debug.setPosition(20, 40);
        debug.setFontScale(.15f);
        debug.setColor(Color.GRAY);

        stage.addActor(debug);


        stage.setViewport(new ScreenViewport());

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        syncWithModel();
    }

    @Override
    public void resize(int width, int height) {
        //Handle resize. This is still important on static windows (eg. Android) because it is
        // called once in the beginning of the app lifecycle, so instead of handling sizing in create,
        // it's clearer to do it here, and avoids doing it twice (create and resize are both called initially)
        //set stage viewport
        stage.getViewport().update(width, height, true);
        //calcZone.setPosition((width-calcZone.getWidth())/2,(height-calcZone.getHeight())/2);
        if (rootTable.getChildren().contains(keyPadTabs, true)) {
            rootTable.clearChildren();
        }

        if (Gdx.graphics.getWidth() > Gdx.graphics.getHeight()) {
            landscape = true;
            size = Gdx.graphics.getWidth() / 10;
            calcZone.setPosition((float) ((Gdx.graphics.getWidth() - ((size * 5))) / 2), (height - calcZone.getHeight()) / 2 + 50);

        } else {
            landscape = false;
            size = (int)(Gdx.graphics.getWidth() / 5);
            calcZone.setPosition((width - calcZone.getWidth()) / 2, (float) (Gdx.graphics.getHeight() - ((size * 4.5) / 4)));


        }
        //KeyPad
        tabNum = 1;

        //KeyPad tab generator, generates 10 different tabs
        keyPadTabs = new Table();

        for (int i = 1; i <= 2; i++) {

            String text="";
            if(i==1){
                text="Classic";
            }else if(i==2){
                text="Scientific";
            }
            final TextButton inputButton = new TextButton(text, skin);
            inputButton.getLabel().setFontScale(.25f);
            keyPadTabs.add(inputButton).width((int)(size*2.5)).height((int)(size / 2.5)).colspan(i);
            if(i==1){
                inputButton.setColor(230,74,25,0.75f);
            }
            final int valueof = i;
            inputButton.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float z, float y) {
                    tabNum = valueof;
                    tabChooser();
                    for(int x=0; x<keyPadTabs.getChildren().size; x++){
                        keyPadTabs.getChildren().get(x).setColor(230,74,25,1);

                    }
                    inputButton.setColor(230,74,25,0.75f);

                }
            });
        }
        keyPadTabs.row();
        prevtabNum = tabNum;
        keypad = new Table();
        tabChooser();


        rootTable.setZIndex(998);
        calcZone.setZIndex(1);
        //Populate rootTable
        rootTable.row().top();
        rootTable.add(toolbarLeft);
        rootTable.add(toolbar).expand().right();
        rootTable.row();
        rootTable.add(keyPadTabs).expandX().right().colspan(2).padBottom(2);
        rootTable.row();
        rootTable.add(keypad).expandX().right().colspan(2);
        keyPadTabs.setVisible(false);
    }

    @Override
    public void render(float delta) {
        //Wipe the screen clean with a white clear color
        Gdx.gl.glClearColor(.9294f, .9294f, .9294f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        //Scene2d. Step forward the world and draw the scene
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
        //stage.setDebugAll(true);

        stage.getBatch().begin();
        stage.getBatch().setColor(Color.BLACK);
        for (int i = 0; i < circle.size() - 1; i++) {
            p1 = circle.get(i);
            p2 = circle.get(i + 1);
            BatchShapeUtils.drawLine(stage.getBatch(), p1[0], p1[1], p2[0], p2[1], 2);
        }
        stage.getBatch().end();
    }

    @Override
    public void pause() {
        model.save();
    }

    @Override
    public void resume() {
        model.load();
    }

    @Override
    public void dispose() {
        //When the app is destroyed, don't leave any memory leaks behind
        stage.dispose();
        skin.dispose();
    }

    @Override
    public void hide(){}

    public void tabChooser() {

        //Choose between the 10 different tabs
        String[][] keys;

        //The arrays for the 10 different tabs (Only the first tab is real right now, N is a placeholder).
        switch (tabNum) {
            case 1:
                keys = new String[][]{
                        {"7", "8", "9", "+", "^"},
                        {"4", "5", "6", "-", "E"},
                        {"1", "2", "3", "*", "("},
                        {"0", "0", ".", "/", ")"}

                };
                break;
            case 2:
                keys = new String[][]{
                        {"sin()", "sin()", "csc()", "csc()", "arcsin"},
                        {"cos()", "cos()", "sec()", "sec()", "arccos"},
                        {"tan()", "tan()", "cot()", "cot()", "arctan"},
                        {"0", "0", ".", "/", "N", "N"}

                };
                break;

            default:
                keys = new String[][]{
                        {"7", "8", "9", "+", "N"},
                        {"4", "5", "6", "-", "N"},
                        {"1", "2", "3", "*", "N"},
                        {"0", "0", ".", "/", "N"}

                };
        }

        //Generate the keypad
        keyPadGenerator(keys);

    }


    public void keyPadGenerator(String[][] keys) {
        //Clear the existing keypad
        keypad.clear();

        //Keypad generator
        for (int x = 0; x < keys.length; x++) {
            for (int y = 0; y < keys[0].length; y++) {
                final String buttonTxt = keys[x][y];

                //Used to keep track of col-span.
                int i = 1;
                //Look for repeated keys
                while (y < keys[0].length - 1 && buttonTxt.equals(keys[x][y + i])) {
                    i++;
                }

                //Skip forward to avoid repetition
                y += i - 1;

                //Make Button, create block at end of row if clicked.
                Container inputButton = new KeypadButton(buttonTxt, model);
                inputButton.width(i * size).height(size);
                keypad.add(inputButton).colspan(i);
            }
            keypad.row();
        }

    }

    private void syncWithModel(){
        Bench.start("sync model");

        calcZone.clear();
        selectedBlock.clearChildren();

        for(ExpressionNode exp : model.getExpressions()){
            Expression expression = expressionMap.get(exp);
            if(expression==null) {
                expression=new Expression(exp);
                expressionMap.put(exp,expression);
            }
            calcZone.addActor(expression);
            expression.row.clearChildren();
            for(BaseNode node : exp.getChildren()){
                if(node instanceof InsertionPoint){
                    expression.row.addActor(new Cursor());
                    continue;
                }
                LabelBlock block = blockMap.get(node);
                if(block==null){
                    block = BlockCreator.BlockCreator(node, model);
                    blockMap.put(node, block);
                }
                if(model.getSelection().size>0 && node == model.getSelection().first()){
                    expression.row.addActor(selectedBlock);
                }
                block.setDraggable(!node.isSelected());
                block.setTargetable(!node.isSelected());
                if(node.isSelected()){
                    selectedBlock.addActor(block);
                    block.getActor().setColor(Color.BLUE);
                } else {
                    block.getActor().setColor(Color.BLACK);
                    expression.row.addActor(block);
                }
            }
            expression.setResult(exp.getResult());
            expression.setPosition(exp.getX()-expression.getPrefWidth()/2,exp.getY()-expression.getPrefHeight()/2);
        }
        if(model.getSelection().size==0)selectedBlock.setVisible(true);
        //Change the color of the redo/undo button to gray if stack is empty.
        if(model.canUndo()){
            undo.setColor(Color.BLACK);
        } else {
            undo.setColor(Color.GRAY);
        }
        if(model.canRedo()) {
            redo.setColor(Color.BLACK);
        } else {
            redo.setColor(Color.GRAY);
        }
        parenthesize.setVisible(model.getSelection().size>0);
        Bench.end("sync model");

    }

    @Override
    public void update() {
        syncWithModel();
    }
}
