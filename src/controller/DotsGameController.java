package controller;

import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import config.Config;
import model.DotSet;
import model.DotsPair;
import model.DotsPairGenerator;
import model.GameLogic;
import model.Player;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.paint.Color;
import view.GameGUI;

/**
 * 
 * (DotsGameController) > DotsPairGenerator > DotsPair > DotSet > Coordinate
 *                                          > Ratio
 *                      > Player
 *                      > DataWriter
 * (DotsGameController) > GameGUI > SetUp
 * 
 * The center of the program; interface between the
 * models and the view. Controls the flow of the assessment.
 * 
 * Classes Related to:
 *  -GameGUI.java (view)
 *      -Updates elements of the GUI as the game progresses and responds.
 *  -DotsPairGenerator.java (model)
 *      -Calls on DotsPairGenerator to generate new DotsPairs.
 *  -DotsPair.java (model)
 *      -LetterGameController keeps track of the most recent DotsPair created in variable currentDotsPair.
 *  -Player.java (model)
 *      -Updates Player information as the game progresses and responds.
 *  -GameLogic.java (model)
 *      -Calls on GameLogic to evaluate the correctness of a response from the subject.
 *  -DataWriter.java
 *      -Passes information (Player and DotsPair) to DataWriter to be exported.
 *      
 * @author Tony Jiang
 * 6-25-2015
 * 
 */
public class DotsGameController implements GameController {
    
    /** Logger */
    private static Logger logger = Logger.getLogger("mylog");
    
    /** Color of the canvas. In this assessment, should be same color as background. */
    final static Color CANVAS_COLOR = Color.web("#000000");
            
    /** Time in milliseconds for the player to get ready after pressing start */
    final static int GET_READY_TIME = 2000;
    
    /** Time in milliseconds to show mask */
    final static int MASK_TIME = 100;
    
    /** On which section should feedback begin to play. */
    public static final int SECTION_TO_START_FEEDBACK = 4;
               
    /** Time between rounds in milliseconds. */
    static int TIME_BETWEEN_ROUNDS;
    
    /** Time in milliseconds that the DotSets flash */
    static int FLASH_TIME;
    
    /** DataWriter to export data to CSV. */
    private DataWriter dataWriter;
    
    /** DotsPairGenerator to generate an DotsPair */
    private DotsPairGenerator dpg;
    /** The graphical user interface. */
    private GameGUI theView;
    /** The current scene. */
    private Scene theScene;
    /** Canvas Graphics Context */
    private GraphicsContext graphicsContextCanvas;
    /** Question Graphics Context */
    private GraphicsContext graphicsContextQuestion;
    /** Preview Question Graphics Context */
    private GraphicsContext graphicsContextPreviewQuestion;
    
    /** Colors to use in each block */
    /** Color of the first DotSet */
    private Color dotsColorOne;
    /** Color of the second DotSet */
    private Color dotsColorTwo;
    
    /** The integer representation of the last round's block. */
    private int lastBlock;
    /** Whether "Yes" is correct or not */
    private boolean yesCorrect;
    /** Number of total dots inputted by user */
    private int subjectTotalDots;
    /** Whether F is for "Yes" or not */
    private boolean FforTrue;
    /** The subject. */
    private Player thePlayer;
    /** The current DotsPair being evaluated by the subject. */
    private DotsPair currentDotsPair;
        
    
    /** Used to measure response time. */
    private static long responseTimeMetric;
    
    /** Current state of the overall game. */
    public static CurrentState state;
    
    /** Describes the current state of gameplay */
    private static GameState gameState;
    private enum GameState {
        /** User is being shown the dots. */
        DISPLAYING_DOTS,

        /** Displaying mask */
        MASK,
        
        /** Question is being shown. Waiting for response from user. Recording reponse time. */
        WAITING_FOR_RESPONSE,
        
        /** Waiting for the player to press space to continue */
        PRESS_SPACE_TO_CONTINUE,
        
        /** Between blocks. (Not active gameplay) */
        CHANGING_BLOCKS,
    }
    
    private QuestionType questionType;
    
	enum QuestionType {
    	WHICH_SIDE_MORE,
    	HOW_MANY_TOTAL,
    }
    
    /**
     * Lock for locking threads.
     */
    private Object lock = new Object();
    /** Number of rounds the player is into the current block. */
    private int numRoundsIntoBlock;
        
    /** Alternate reference to "this" to be used in inner methods */
    private DotsGameController gameController;
        
    private static boolean feedback_given;
    
    private Random randomGenerator = new Random();
    
    /** 
     * Constructor for the controller. There is only meant
     * to be one instance of the controller. Attaches listener
     * for when user provides response during trials. On a response,
     * prepare the next round and record the data.
     * @param view The graphical user interface.
     */
    public DotsGameController(GameGUI view) {
        
        loadConfig();
        
        this.gameController = this;
        this.dpg = new DotsPairGenerator();
        this.determineBlockMode();
        this.currentDotsPair = null;
        this.theView = view;
        this.theScene = view.getScene();
        this.thePlayer = new Player();
        this.dataWriter = new DataWriter(this);
        this.updateDotColors();
        this.setFandJ();
    }
    


    /** 
     * Load configuration settings. 
     */
    private void loadConfig() {
        new Config();
        FLASH_TIME = Config.getPropertyInt("flash.time");
        TIME_BETWEEN_ROUNDS = Config.getPropertyInt("time.between.rounds");
    }
    
    /**
     * Determine which of F and J is for "Yes"/"No".
     */
    private void setFandJ() {
        if (randomGenerator.nextBoolean()) {
            this.FforTrue = true;
        } else {
            this.FforTrue = false;
        }
    }
    
    /**
     * Sets event listener for when subject clicks the start button or presses Enter.
     * Pass in the subject's ID number entered.
     */
    public void setLoginHandlers() {
        this.theScene = theView.getScene();
        this.theView.getStart().setOnAction(e -> 
            {
                onClickStartButton();
            });
        this.theScene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode() == KeyCode.ENTER) {                
                    onClickStartButton();
                }
            }
        });
        this.theScene.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
            public void handle(final KeyEvent keyEvent) {
                if (keyEvent.getCode() == KeyCode.ESCAPE) {
                    theView.showExitPopup();
                    theView.getExitPopup().getContent().get(0).toFront();
                    keyEvent.consume();
                }
            }
        });
    }
    
    /**
     * Action to be executed upon clicking of Start on Login screen.
     * 
     * Records user inputted data and sets instructions screen.
     */
    private void onClickStartButton() {
        theView.getFeedback().setVisible(false);
        theView.getFeedbackAge().setVisible(false);
        theView.getFeedbackGender().setVisible(false);
        try {
            gameController.thePlayer.setSubjectID(theView.getEnterId().getText());
        } catch (NumberFormatException ex) {
            theView.getEnterId().requestFocus();
            theView.getEnterId().setText("");
            theView.getFeedback().setVisible(true);
            return;
        }    
        if (theView.getPickMale().isSelected()) {
            gameController.thePlayer.setSubjectGender(Player.Gender.MALE);
        } else if (theView.getPickFemale().isSelected()) {
            gameController.thePlayer.setSubjectGender(Player.Gender.FEMALE);
        } else {
            theView.getFeedbackGender().setVisible(true);
            return;
        }
        try {
            gameController.thePlayer.setSubjectAge(Integer.parseInt(theView.getEnterAge().getText()));
        } catch (NumberFormatException ex) {
            theView.getEnterAge().requestFocus();
            theView.getEnterAge().setText("");
            theView.getFeedbackAge().setVisible(true);
            return;
        }
        theView.setInstructionsScreen(); 
        this.setKeyGuides();
        logger.log(Level.INFO, "Subject ID: " + thePlayer.getSubjectID());
        logger.log(Level.INFO, "Subject Gender: " + thePlayer.getSubjectGender());
        logger.log(Level.INFO, "Subject Age: "  + thePlayer.getSubjectAge());
    }
    
    /** 
     * Set event listener on the Next button. 
     */
    public void setInstructionsHandlers() {
        this.theScene = theView.getScene();
        this.theView.getNext().setOnAction(e -> {
            onClickNextInstructions();
        });
        this.theScene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode() == KeyCode.ENTER) {
                    onClickNextInstructions();
                }
            }
        });
    }
    
    /**
     *  Actions to be executed on clicking the Next button 
     */
    private void onClickNextInstructions() {
        theView.setGameScreen(); 
        state = CurrentState.PRACTICE;
    }
    
    /**
     * "Cycle" = Practice or Block
     * Set handler upon clicking the "Start Assessment" button, preparing for next block.
     * Sets the game screen and the state to GAMEPLAY and removes the "Practice" label.
     * Resets the player's data if coming from practice.
     */
    public void setCycleCompleteHandlers(CurrentState isPractice) {
        this.theView.getStartAssessment().setOnAction( e-> {
            theView.setGameScreen();
            theView.getPractice().setVisible(false);
            state = CurrentState.GAMEPLAY;
            gameState = GameState.CHANGING_BLOCKS;
            if (isPractice == CurrentState.PRACTICE) {
                this.resetPlayer();
            }
        });
    }
    
    /** 
     * Reset the player data, but retain intrinsic subject data 
     */
    private void resetPlayer() {
        String subjectID = thePlayer.getSubjectID();
        Player.Gender subjectGender = thePlayer.getSubjectGender();
        SimpleIntegerProperty subjectAge = new SimpleIntegerProperty(thePlayer.getSubjectAge());
        thePlayer = new Player(subjectID, subjectGender, subjectAge);
    }
    
    /** 
     * Sets event listener for when subject presses 'F' or 'J' key
     * during a round. 
     */
    public void setGameHandlers() {
        this.theScene = theView.getScene();
        this.theScene.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if ((event.getCode() == KeyCode.F 
                        || event.getCode() == KeyCode.J) 
                        && !feedback_given
                        && (gameState == GameState.WAITING_FOR_RESPONSE 
                        || (questionType == QuestionType.WHICH_SIDE_MORE
                        	&& (gameState == GameState.MASK
                        		|| gameState == GameState.DISPLAYING_DOTS)))) {
                    gameController.handlePressForJ(event);
                }
            }
        });
        this.theScene.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent key) {
                if (key.getCode() == KeyCode.SPACE
                        && gameState == GameState.PRESS_SPACE_TO_CONTINUE) {
                    theView.getPressSpaceText().setText("");
                    setOptions();
                    gameState = GameState.DISPLAYING_DOTS;
                }
            }
        });
        theView.getNumberSlider().valueProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> o, Number oldValue, Number newValue) {
				theView.getNumberSliderPreview().setText("" + newValue.intValue());
			}
        });
    }
    
    /**
     * Actions to be executed on the pressing of the F or J key.
     * Update the models/data, prepare the next round, and export data to CSV.
     * @param event
     */
    private void handlePressForJ(KeyEvent event) {
        feedback_given = true;
        this.responseAndUpdate(event);
        if (gameState == GameState.WAITING_FOR_RESPONSE) {
            this.prepareNextRound(); 
        } else if (gameState == GameState.DISPLAYING_DOTS) {
            this.clearRound();
        } else if (gameState == GameState.MASK) {
            theView.getMask().setVisible(false);
            clearQuestion();
        }
        this.checkIfBlockDone();
        this.checkIfDone();
        this.exportDataToCSV();
    }
    
    /** 
     * Export data to CSV file.
     */
    private void exportDataToCSV() {
        if (state == CurrentState.GAMEPLAY) {
            dataWriter.writeToCSV();    
        }
    }
    
    /**
     * Update models and view appropriately according to correctness
     * of subject's response.  
     * @param e The key event to check which key the user pressed.
     * @param view the graphical user interface
     * @return True if the player is correct. False otherwise.
     */
    public void responseAndUpdate (
            KeyEvent e) {
        if (state != CurrentState.PRACTICE) {
            this.numRoundsIntoBlock++;
        }
        DotsPair dp = this.currentDotsPair;
        boolean correct = false;
        if (this.questionType == QuestionType.WHICH_SIDE_MORE) {
            this.setYesCorrect(GameLogic.checkWhichSideCorrect(dp, dpg.getBlockMode()));
            correct = GameLogic.checkAnswerCorrect(e, this.yesCorrect, this.FforTrue);
            //this.feedbackSound(correct);
        } else if (this.questionType == QuestionType.HOW_MANY_TOTAL) {
            this.setSubjectTotalDots(theView.getNumberSlider().valueProperty().getValue().intValue());
        }
        this.updatePlayer(correct);
        this.dataWriter.grabData(this);
    }
    
    /** Update the player appropriately.
     * 
     * @param currentPlayer The current player.
     * @param correct True if subject's reponse is correct. False otherwise.
     */
    private void updatePlayer(boolean correct) {
        Player currentPlayer = this.thePlayer;
        this.recordResponseTime();
        if (correct) {
            currentPlayer.addPoint();
            currentPlayer.setRight(true);
        } else {
            currentPlayer.setRight(false);
        }
        currentPlayer.incrementNumRounds();
    }
    
//    /** If user inputs correct answer play positive feedback sound,
//     * if not then play negative feedback sound.
//     * @param feedbackSoundFileUrl the File Url of the Sound to be played.
//     * @param correct whether the subject answered correctly or not.
//     */
//    private void feedbackSound(boolean correct) {
//        URL feedbackSoundFileUrl;
//        if (correct) {
//            feedbackSoundFileUrl = 
//                    getClass().getResource("/res/sounds/Ping.aiff");
//        } else {
//            feedbackSoundFileUrl = 
//                    getClass().getResource("/res/sounds/Basso.aiff");
//        }
//        if (true) {
//            new AudioClip(feedbackSoundFileUrl.toString()).play();
//        }
//    }
    
    /**
     * Set the text that lets the user know which of the F and J keys
     * are "Yes"/"No".
     */
    private void setKeyGuides() {
        if (this.FforTrue) {
            theView.getLeftKeyGuide().setText("F = Yes");
            theView.getRightKeyGuide().setText("J = No");
        } else {
            theView.getLeftKeyGuide().setText("F = No");
            theView.getRightKeyGuide().setText("J = Yes");
        }
    }
    
    /**
     * Prepare the first round by making a load bar to 
     * let the subject prepare for the first question.
     * 
     * Also sets up the canvases on which the dots will be painted.
     */
    public void prepareFirstRound() {
        Task<Void> sleeper = new Task<Void>() {   
            @Override
            protected Void call() throws Exception {
                for (int i = 0; i < GET_READY_TIME; i++) {
                    this.updateProgress(i, GET_READY_TIME); 
                    Thread.sleep(1);
                }
                return null;
            }
        };
        theView.getGetReadyBar().progressProperty().bind(sleeper.progressProperty());
        sleeper.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent e) {
                gameState = GameState.DISPLAYING_DOTS;
                graphicsContextCanvas = theView.getDotsCanvas().getGraphicsContext2D();
                setOptions();
                responseTimeMetric = System.nanoTime();
                theView.getGetReadyBox().setVisible(false);
            }
        });
        Thread sleeperThread = new Thread(sleeper);
        sleeperThread.start();
    }
    
    /**
     * Shows the user the screen to prompt pressing of space to continue.
     */
    public void prepareNextRound() {
        this.clearQuestion(); 
    }
    
    /**
     * Checks if the current block should be completed based on number of rounds.
     */
    private void checkIfBlockDone() {
        this.lastBlock = this.dpg.getBlockMode();
        if (this.numRoundsIntoBlock >= NUM_QUESTIONS_PER_BLOCK) {
            this.numRoundsIntoBlock = 0;
            this.dpg.changeBlock();
            this.determineBlockMode();
            this.updateDotColors();
            theView.setBlockCompleteScreen(dpg.getBlockMode(), dotsColorOne, dotsColorTwo);
            if (dpg.getBlockMode() <= 3) {
            	paintDotsInPreviewQuestion();
            }
            gameState = GameState.CHANGING_BLOCKS;
        }
    }
    
    private void paintDotsInPreviewQuestion() {
        graphicsContextPreviewQuestion = theView.getPreviewQuestionCanvas().getGraphicsContext2D();
    	graphicsContextPreviewQuestion.setFill(dotsColorOne);
    	graphicsContextPreviewQuestion.fillOval(350.0, 55.0, 50.0, 50.0);
    	graphicsContextPreviewQuestion.setFill(dotsColorTwo);
    	graphicsContextPreviewQuestion.fillOval(700.0, 55.0, 50.0, 50.0);
    }
    
    /**
     * Get a new random pair of dot colors for the next block.
     */
    private void updateDotColors() {
        switch (this.dpg.getBlockMode()) {
        case DotsPairGenerator.GREENISH_MORE_BLOCK:
        case DotsPairGenerator.GREENISH_TOTAL_BLOCK:
        	dotsColorOne = Color.web("#B4FF00");
        	dotsColorTwo = Color.web("#00FF96");
        	break;
        case DotsPairGenerator.YELLOW_BLUE_MORE_BLOCK:
        case DotsPairGenerator.YELLOW_BLUE_TOTAL_BLOCK:
        	dotsColorOne = Color.web("#FFFF00");
        	dotsColorTwo = Color.web("#0000FF");
        	break;
        case DotsPairGenerator.YELLOWISH_BLUISH_MORE_BLOCK:
        case DotsPairGenerator.YELLOWISH_BLUISH_TOTAL_BLOCK:
        	dotsColorOne = Color.web("#DCFF00");
        	dotsColorTwo = Color.web("#00FFC8");
        	break;
        case DotsPairGenerator.GREEN_MORE_BLOCK:
        case DotsPairGenerator.GREEN_TOTAL_BLOCK:
        	dotsColorOne = Color.web("#64FF00"); //R100,G255,B0
        	dotsColorTwo = Color.web("#00FF64"); //R0, G255, B100
        	break;
        }
    }
    /** 
     * Check if subject has completed practice or assessment.
     */
    private void checkIfDone() {
        if (this.thePlayer.getNumRounds() >= NUM_ROUNDS) {
            this.finishGame();
        } else if (state == CurrentState.PRACTICE && thePlayer.getNumRounds() >= NUM_PRACTICE_ROUNDS) {
            this.finishPractice();
        }
    } 
    
    /**
     * If subject has completed the total number of rounds specified,
     * then change the scene to the finish screen.
     */
    private void finishGame() {
        theView.setFinishScreen(thePlayer.getNumCorrect());
        theView.getScene().setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode() == KeyCode.ESCAPE) {
                    System.exit(0);
                }
            }
        });
    }
  
    /**
     * If subject has completed the total number of rounds specified,
     * then change the scene to the practice complete screen.
     */
    private void finishPractice() {
        theView.setPracticeCompleteScreen(dpg.getBlockMode(), dotsColorOne, dotsColorTwo);
        if (dpg.getBlockMode() <= 3) {
            paintDotsInPreviewQuestion();
        }
        theView.getScene().setOnKeyPressed(null);
        gameState = GameState.CHANGING_BLOCKS;
        state = CurrentState.PRACTICE_FINISHED;
        this.dpg.clearRatios();
    }
    
    /**
     * Clears the options.
     */
    public void clearRound() {
        theView.getDotsCanvas().setOpacity(0);
        graphicsContextCanvas.setFill(CANVAS_COLOR);
        graphicsContextCanvas.fillRect(0, 0, theView.getDotsCanvas().getWidth(),theView.getDotsCanvas().getHeight());
        
        this.showMask();
    }

    /**
     * Show the question to user based on current block and colors.
     */
    private void setTheQuestion() {
    	//determineBlockMode();
        if (this.questionType == QuestionType.WHICH_SIDE_MORE) {
            theView.getQuestion().setText("Is        more than         ?");
            theView.getQuestionCanvas().setOpacity(1.0);
            paintDotsInQuestion();
        } else if (this.questionType == QuestionType.HOW_MANY_TOTAL) {
        	theView.getQuestion().setText("How many dots are there in total?");
        	theView.getNumberSlider().setVisible(true);
        	theView.getNumberSliderPreview().setVisible(true);
        	theView.getScene().setCursor(Cursor.DEFAULT);
        } else {
        	logger.severe("DotsGameController->setTheQuestion(): Unknown question type");
        }
        theView.getQuestion().toFront();
    }
    
    private void determineBlockMode() {
    	if (dpg.getBlockMode() <= 3) {
    		this.questionType = QuestionType.WHICH_SIDE_MORE;
    	} else if (dpg.getBlockMode() <= 7) {
    		this.questionType = QuestionType.HOW_MANY_TOTAL;
    	} else {
    		logger.severe("DotsGameController->determineBlockMode(): "
    				+ "Could not map dpg.blockMode to a QuestionType.");
    	}
    }
    
    /**
     * Paint the dots inside the question.
     */
    private void paintDotsInQuestion() {
        graphicsContextQuestion = theView.getQuestionCanvas().getGraphicsContext2D();
    	graphicsContextQuestion.setFill(dotsColorOne);
    	graphicsContextQuestion.fillOval(350.0, 50.0, 50.0, 50.0);
    	graphicsContextQuestion.setFill(dotsColorTwo);
    	graphicsContextQuestion.fillOval(700.0, 50.0, 50.0, 50.0);
    }

    /** 
     * Show the mask for MASK_TIME milliseconds then either:
     *  1.) If user has not answered - show the question
     *  2.) If user has answered - tell user to press space to continue
     */
    private void showMask() {
        DotsGameController.gameState = GameState.MASK;
        theView.getMask().setVisible(true);
        Task<Void> sleeper = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int i = 0;
                logger.info(gameState.toString());
                while (i < MASK_TIME) {
                    synchronized (lock) {
                        if (gameState == GameState.MASK) {
                            this.updateProgress(i, MASK_TIME); 
                            Thread.sleep(1);
                            i++;
                        }
                    }
                }
                return null;
            }
        };
        sleeper.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent e) {
                DotsGameController.gameState = GameState.WAITING_FOR_RESPONSE; 
                theView.getMask().setVisible(false); 
                if (!feedback_given) {
                    setTheQuestion();
                } else {
                    clearQuestion();
                }
            }
        });
        Thread sleeperThread = new Thread(sleeper);
        sleeperThread.start();
    }
    
    /**
     * Clear the question and tell the user to press space to continue.
     */
    private void clearQuestion() {
        gameState = GameState.PRESS_SPACE_TO_CONTINUE;
        theView.getQuestion().setText("");
        theView.getQuestionCanvas().setOpacity(0.0);
        theView.getNumberSlider().setVisible(false);
        theView.getNumberSliderPreview().setVisible(false);
    	theView.getPressSpaceText().setText("Press space to continue");
    	theView.getNumberSlider().setValue(25.0);
    	theView.getNumberSliderPreview().setText("" +
    	theView.getNumberSlider().valueProperty().getValue().intValue());
    	theView.getScene().setCursor(Cursor.NONE);
    }
    
    /**
     * Set and show the next round's choices.
     */
    public void setOptions() {
        this.prepareNextPair();
        this.paintDots();
        responseTimeMetric = System.nanoTime();
        this.hideDots();
        feedback_given = false;
    }
    
    /**
     * Prepare the next pair.
     */
    private void prepareNextPair() {
        dpg.getNewModePair();
        this.currentDotsPair = dpg.getDotsPair();
    }
    
    /**
     * Show the choices.
     */
    private void paintDots() {
        theView.getQuestion().setText("");
        theView.getDotsCanvas().setOpacity(1.0);
        
        DotSet dotSetOne = this.currentDotsPair.getDotSetOne();
        DotSet dotSetTwo = this.currentDotsPair.getDotSetTwo();
        graphicsContextCanvas.setFill(dotsColorOne);
        this.paintDotSet(dotSetOne, graphicsContextCanvas);
        graphicsContextCanvas.setFill(dotsColorTwo);
        this.paintDotSet(dotSetTwo, graphicsContextCanvas);
    }
    
    /**
     * Hide the dot sets after some time (FLASH_TIME) has passed.
     */
    private void hideDots() { 
        Task<Void> sleeper = new Task<Void>() {
            @Override
            protected java.lang.Void call() throws Exception {
                int i = 0;
                 
                while (i < FLASH_TIME) {
                    synchronized (lock) {
                        this.updateProgress(i, FLASH_TIME);
                        Thread.sleep(1);
                        i++;
                        /** Quit and exit once F or J pressed */
                        if (feedback_given == true) {
                            return null;
                        }
                    }
                }
                
                return null;
            }    
        };
        sleeper.setOnSucceeded(new EventHandler<WorkerStateEvent>() {
            @Override
            public void handle(WorkerStateEvent event) {
                if (gameState == GameState.DISPLAYING_DOTS && feedback_given == false) {
                    gameController.clearRound();    
                }
            }
        });
        new Thread(sleeper).start();
    }

    /**
     * Paint the dots for a given dotset.
     * @param dotSet - the dotSet to be painted.
     * @param graphicsContext
     */
    private void paintDotSet(DotSet dotSet, GraphicsContext graphicsContext) {
        for (int i = 0; i < dotSet.getTotalNumDots(); i++) {
            
            int x = dotSet.getPositions().get(i).x;
            int y = dotSet.getPositions().get(i).y;

            graphicsContext.fillOval(x, y, 
                    dotSet.getDiameters().get(i), 
                    dotSet.getDiameters().get(i));
        }
    }

    /** 
     * Record the response time of the subject. 
     * responseTimeMetric should be set whenever the dots are shown.
     */
    public void recordResponseTime() {
        long responseTime = System.nanoTime() - responseTimeMetric;
        thePlayer.setResponseTime(responseTime);
        logger.info("Response time: " + responseTime / 1000000000.0);
    }
    
    public Player getThePlayer() {
        return thePlayer;
    }

    public void setThePlayer(Player thePlayer) {
        this.thePlayer = thePlayer;
    }

    public DotsPair getCurrentDotsPair() {
        return currentDotsPair;
    }

    public void setCurrentDotsPair(DotsPair currentDotsPair) {
        this.currentDotsPair = currentDotsPair;
    }

    public DotsPairGenerator getDpg() {
        return dpg;
    }

    public void setApg(DotsPairGenerator dpg) {
        this.dpg = dpg;
    }
    
    public GameGUI getTheView() {
        return theView;
    }
    
    public void setTheScene(Scene scene) {
        this.theScene = scene;
    }

    public boolean isYesCorrect() {
        return yesCorrect;
    }

    public void setYesCorrect(boolean yesCorrect) {
        this.yesCorrect = yesCorrect;
    }

    public int getLastBlock() {
        return lastBlock;
    }

    public void setLastBlock(int lastBlock) {
        this.lastBlock = lastBlock;
    }
    
    public boolean isFforTrue() {
        return FforTrue;
    }

    public void setFforTrue(boolean fforTrue) {
        FforTrue = fforTrue;
    }

    public Color getDotsColorOne() {
    	return this.dotsColorOne;
    }
    
    public Color getDotsColorTwo() {
    	return this.dotsColorTwo;
    }
    
    public QuestionType getQuestionType() {
		return questionType;
	}

	public void setQuestionType(QuestionType questionType) {
		this.questionType = questionType;
	}



	public int getSubjectTotalDots() {
		return subjectTotalDots;
	}



	public void setSubjectTotalDots(int subjectTotalDots) {
		this.subjectTotalDots = subjectTotalDots;
	}

}