# Design of Chess Game in Java for Low Level Design Interviews using Factory and Strategy Design Patterns

Problem Statement: https://codezym.com/question/8-design-chess-game

Design of Game of Chess is all about different pieces and their moves. There are only two things that you need to figure out and rest is easy.

- How will you create the different chess pieces
- How will you implement the different moves of these pieces

This article primarily aims to prepare you for this question in context of a Low Level Design Interview.

We will not go into design of a large system like chess.com which has tens of functionalities and hundreds of classes.


We will discuss the requirements, then build our solution using multiple classes. We will also see how using Factory design pattern makes it simpler to create different chess pieces. Also, strategy pattern makes it easy to reuse the common moves of different pieces.

Finally, we will have complete Java code, and you can test it on above CodeZym link.

---

## Just a bit of recap..

Game of Chess is played on a board with 8 rows and 8 columns.

There are total 32 pieces, 16 of white and 16 of black. 6 different types of pieces are there.

King can move 1 step and Queen can move any number of steps in vertical, horizontal or diagonal direction.

Rook can move vertically or horizontally, and bishop can only move diagonally.

Knight makes 2+1 moves and it can also jump over other pieces in its path.

Pawn moves only 1 step forward, it moves straight and kills diagonally.

---

## Representing the ChessBoard

We will represent our chess board using a 2-d array. Class `ChessBoard` will keep this 2-d array and also store the game state.

W for white B for Black,

R for rook, B for Bishop, H for knight, Q for Queen, K for king and P for pawn.

e.g. `WB` is white bishop, `BK` is black king, empty strings represent empty spaces.

---

## Requirements

- check whether we can move a piece which is at starting row, column to another row, column
- get game status whether game is in progress or black has won or white has won
- get whose turn is it to play next white, black or if game is already finished

---

## Breaking in multiple classes

We will start with the classes which don’t depend on other classes and gradually move to other classes. Ours will be a bottom to top approach for class design.

Lets start with the move classes. All though each piece has their own move, if you look closely, you will find that Queen makes straight and diagonal moves, rook makes straight moves and bishop makes diagonal moves. hence there is scope of reusing Straight and diagonal moves to create these classes.

We will use strategy design pattern. Interface `Move` will be the generic contract and both `StraightMove` and `DiagonalMove` will implement it. `StraightMove` contains logic for both horizontal and vertical move.

```java
interface Move{
    boolean canMove(ChessBoard board, int startRow,
                    int startCol, int endRow, int endCol);
}

class DiagonalMove implements Move{
public boolean canMove(ChessBoard board, int startRow,
                       int startCol, int endRow, int endCol) {
    int rowDelta=endRow-startRow;
    int colDelta=endCol-startCol;
    if(Math.abs(rowDelta)!=Math.abs(colDelta)) return false;
    colDelta=colDelta>0?1:-1;
    rowDelta=rowDelta>0?1:-1;
    while(startRow!=endRow){
        if(board.getPiece(startRow,startCol)!=null) return false;
        startRow+=rowDelta;
        startCol+=colDelta;
    }
    return true;
}
}

class StraightMove implements Move{
public boolean canMove(ChessBoard board, int startRow,
                       int startCol, int endRow, int endCol) {
    int rowDelta=endRow-startRow;
    int colDelta=endCol-startCol;
    if(endRow!=startRow && endCol!=startCol) return false;

    if(startRow==endRow){
      colDelta=colDelta>0?1:-1;
      startCol+=colDelta;
      while(startCol!=endCol){
          if(board.getPiece(startRow, startCol)!=null) return false;
          startCol+=colDelta;
      }
    }
    if(startCol==endCol){
        rowDelta=rowDelta>0?1:-1;
        startRow+=rowDelta;
        while(startRow!=endRow){
            if(board.getPiece(startRow, startCol)!=null) return false;
            startRow+=rowDelta;
        }
    }
    return true;
}
}
```

Both above move classes are used by chess `Piece` class to implement default movement. `Piece` class is the generic class which takes an array of `Move` objects and in its `canMove()` method checks whether it is possible to go from start to end position using any of the moves that the `Piece` object has.

```java
class Piece{
 private char color, type;
 private Move moves[];

 Piece(char color, char type){
   this.color=color;
   this.type=type;
   moves=new Move[0];
 }

 Piece(char color, char type, Move moves[]){
     this(color,type);
     this.moves=moves;
 }

 protected boolean canMove(ChessBoard board,
                           int startRow, int startCol, int endRow, int endCol){
     for(Move move:moves) {
         if (move.canMove(board, startRow,
                 startCol, endRow, endCol)) return true;
     }
     return false;
}

char getColor(){
    return color;
}

char getType(){
   return type;
}
}
```

Also King, Knight and Pawn implement their own `canMove()` method. They don’t share any moves. All of these are subclasses of class `Piece`.

```java
class KingPiece extends Piece{
KingPiece(char color, char type) {
    super(color, type);
}
public boolean canMove(ChessBoard board,
                       int startRow, int startCol, int endRow, int endCol){
  int rowDelta=Math.abs(endRow-startRow);
  int colDelta=Math.abs(endCol-startCol);
  return rowDelta<=1 && colDelta<=1;
}
}

class KnightPiece extends Piece{
KnightPiece(char color, char type) {
    super(color, type);
}
public boolean canMove(ChessBoard board,
                       int startRow, int startCol, int endRow, int endCol){
    int rowDelta=Math.abs(endRow-startRow);
    int colDelta=Math.abs(endCol-startCol);
    return (rowDelta==2&&colDelta==1) || (rowDelta==1&&colDelta==2);
}
}

class PawnPiece extends Piece{
PawnPiece(char color, char type) {
    super(color, type);
}
public boolean canMove(ChessBoard board,
     int startRow, int startCol, int endRow, int endCol){

  Piece pawn=board.getPiece(startRow, startCol);
  if(board.getPiece(endRow, endCol)==null){
      if(startCol!=endCol) return false;
      return (pawn.getColor()=='W' && endRow-startRow==1)
             ||  (pawn.getColor()=='B' && endRow-startRow==-1);
  }
  else{
      if(Math.abs(startCol-endCol)!=1) return false;
      return (pawn.getColor()=='W' && endRow-startRow==1)
              ||  (pawn.getColor()=='B' && endRow-startRow==-1);
  }
}
}
```

If you look at how these `Piece` objects are created, then it will become clear. Lets go through the code in `ChessPieceFactory` class.

```java
class ChessPieceFactory{
private Move straightMove=new StraightMove();
private Move diagonalMove=new DiagonalMove();
private static ChessPieceFactory instance;
private ChessPieceFactory(){}
public static ChessPieceFactory getInstance(){
     if(instance!=null) return instance;
     synchronized (ChessPieceFactory.class){
       if(instance==null)instance=new ChessPieceFactory();
     }
     return instance;
}

Piece createPiece(char color, char type){
  switch (type){
      case 'Q': return new Piece(color, type,
    new Move[]{straightMove, diagonalMove});

      case 'R': return new Piece(color, type,
              new Move[]{straightMove});

      case 'B': return new Piece(color, type,
              new Move[]{diagonalMove});

      case 'K': return new KingPiece(color, type);
      case 'H': return new KnightPiece(color, type);
      case 'P': return new PawnPiece(color, type);
  }
  return null;
}
}
```

As you can see in above `createPiece()` method,

- queen is initialized with `straightMove` and `diagonalMove`
- rook objects are initialized with `straightMove`
- while bishop objects are initialized with `diagonalMove`

Since `KingPiece`, `KnightPiece` and `PawnPiece` don’t use either straight or diagonal moves so moves array is not passed in their constructor.

Using a factory makes the creation logic of chess pieces simple, just pass the type and color and you get a chess piece object with its moves.

In this Java solution, `ChessPieceFactory` is also implemented as a Singleton. This means only one object of `ChessPieceFactory` is created and reused.

---

## ChessBoard class

Finally let's see the `ChessBoard` class which controls the game. It is initialized with 2-d string array that we saw above. And in its constructor it creates `Piece` objects.

In its `move()` method it uses the `canMove()` method in `Piece` objects before making a move at corresponding start position.

It also tracks `gameState` and `nextTurn`.

```java
class ChessBoard{
private Piece board[][];
private ChessPieceFactory factory = ChessPieceFactory.getInstance();
// 0 for game in progress, 1 for white has won, 2 for black has won
private int gameState=0;
// 0 for white , 1 for black,
private int nextTurn=0;

ChessBoard(String[][] chessboard){
  board=new Piece[chessboard.length][chessboard[0].length];
  for(int row=0;row<board.length;row++)
      for(int col=0;col<board[0].length;col++)
        if(chessboard[row][col].length()>=2){
          char color=chessboard[row][col].charAt(0);
          char type=chessboard[row][col].charAt(1);
          board[row][col] = factory.createPiece(color, type);
      }
}

String move(int startRow, int startCol, int endRow, int endCol){
 if(gameState!=0) return "invalid";
 Piece startPiece=getPiece(startRow, startCol);
 Piece endPiece=getPiece(endRow, endCol);
 if(startPiece==null ||!isValid(endRow, endCol))
     return "invalid";
 if(endPiece!=null && endPiece.getColor()
         ==startPiece.getColor()) return "invalid";
 if(!startPiece.canMove(this, startRow,
     startCol, endRow, endCol)) return "invalid";
 board[startRow][startCol]=null;
 board[endRow][endCol]=startPiece;
 nextTurn=nextTurn==0?1:0;
 if(endPiece!=null && endPiece.getType()=='K')
     gameState=endPiece.getColor()=='B'?1:2;
 if(endPiece==null) return "";
//  return "WP";
 return ""+endPiece.getColor()+endPiece.getType();
}

Piece getPiece(int row, int col){
    if(!isValid(row, col)) return null;
    return board[row][col];
}

int getNextTurn(){
    return nextTurn;
}

int getGameState(){
    return gameState;
}

private boolean isValid(int row, int col) {
    return row>=0 && row<board.length && col>=0 && col<board[0].length;
}
}
```

---

## Solution class

`Solution` class is the entry point expected by CodeZym. It exposes methods to initialize the chess board, make a move, get game status and get next turn.

```java
public class Solution implements Q08ChessBoardInterface {
private Helper08 helper;
ChessBoard board;
public Solution(){}

public void init(Helper08 helper, String[][] chessboard) {
  this.helper=helper;
  board=new ChessBoard(chessboard);
}

// returns "invalid" for invalid move, empty string "" for success
// and "WH", "WQ" etc to represent the piece killed, WQ=white queen
public String move(int startRow, int startCol, int endRow, int endCol) {
    return board.move(startRow, startCol, endRow, endCol);
}

// return 0 for game in progress, 1 for white has won, 2 for black has won
public int getGameStatus() {
    return board.getGameState();
}

// return 0 for white, 1 for black, -1 for game already finished
public int getNextTurn() {
    if(board.getGameState()!=0) return -1;
    return board.getNextTurn();
}
}
```

---

## Complete Java Code

```java
/* ****** Copy this default code to your local code editor
and after completing solution, paste it back here for testing ******** */

interface Move{
    boolean canMove(ChessBoard board, int startRow,
                    int startCol, int endRow, int endCol);
}

public class Solution implements Q08ChessBoardInterface {
private Helper08 helper;
ChessBoard board;
public Solution(){}

public void init(Helper08 helper, String[][] chessboard) {
  this.helper=helper;
  board=new ChessBoard(chessboard);
}

// returns "invalid" for invalid move, empty string "" for success
// and "WH", "WQ" etc to represent the piece killed, WQ=white queen
public String move(int startRow, int startCol, int endRow, int endCol) {
    return board.move(startRow, startCol, endRow, endCol);
}

// return 0 for game in progress, 1 for white has won, 2 for black has won
public int getGameStatus() {
    return board.getGameState();
}

// return 0 for white, 1 for black, -1 for game already finished
public int getNextTurn() {
    if(board.getGameState()!=0) return -1;
    return board.getNextTurn();
}
}

class ChessBoard{
private Piece board[][];
private ChessPieceFactory factory = ChessPieceFactory.getInstance();
// 0 for game in progress, 1 for white has won, 2 for black has won
private int gameState=0;
// 0 for white , 1 for black,
private int nextTurn=0;

ChessBoard(String[][] chessboard){
  board=new Piece[chessboard.length][chessboard[0].length];
  for(int row=0;row<board.length;row++)
      for(int col=0;col<board[0].length;col++)
        if(chessboard[row][col].length()>=2){
          char color=chessboard[row][col].charAt(0);
          char type=chessboard[row][col].charAt(1);
          board[row][col] = factory.createPiece(color, type);
      }
}

String move(int startRow, int startCol, int endRow, int endCol){
 if(gameState!=0) return "invalid";
 Piece startPiece=getPiece(startRow, startCol);
 Piece endPiece=getPiece(endRow, endCol);
 if(startPiece==null ||!isValid(endRow, endCol))
     return "invalid";
 if(endPiece!=null && endPiece.getColor()
         ==startPiece.getColor()) return "invalid";
 if(!startPiece.canMove(this, startRow,
     startCol, endRow, endCol)) return "invalid";
 board[startRow][startCol]=null;
 board[endRow][endCol]=startPiece;
 nextTurn=nextTurn==0?1:0;
 if(endPiece!=null && endPiece.getType()=='K')
     gameState=endPiece.getColor()=='B'?1:2;
 if(endPiece==null) return "";
//  return "WP";
 return ""+endPiece.getColor()+endPiece.getType();
}

Piece getPiece(int row, int col){
    if(!isValid(row, col)) return null;
    return board[row][col];
}

int getNextTurn(){
    return nextTurn;
}

int getGameState(){
    return gameState;
}

private boolean isValid(int row, int col) {
    return row>=0 && row<board.length && col>=0 && col<board[0].length;
}
}

class ChessPieceFactory{
private Move straightMove=new StraightMove();
private Move diagonalMove=new DiagonalMove();
private static ChessPieceFactory instance;
private ChessPieceFactory(){}
public static ChessPieceFactory getInstance(){
     if(instance!=null) return instance;
     synchronized (ChessPieceFactory.class){
       if(instance==null)instance=new ChessPieceFactory();
     }
     return instance;
}

Piece createPiece(char color, char type){
  switch (type){
      case 'Q': return new Piece(color, type,
    new Move[]{straightMove, diagonalMove});

      case 'R': return new Piece(color, type,
              new Move[]{straightMove});

      case 'B': return new Piece(color, type,
              new Move[]{diagonalMove});

      case 'K': return new KingPiece(color, type);
      case 'H': return new KnightPiece(color, type);
      case 'P': return new PawnPiece(color, type);
  }
  return null;
}
}

class Piece{
 private char color, type;
 private Move moves[];

 Piece(char color, char type){
   this.color=color;
   this.type=type;
   moves=new Move[0];
 }

 Piece(char color, char type, Move moves[]){
     this(color,type);
     this.moves=moves;
 }

 protected boolean canMove(ChessBoard board,
                           int startRow, int startCol, int endRow, int endCol){
     for(Move move:moves) {
         if (move.canMove(board, startRow,
                 startCol, endRow, endCol)) return true;
     }
     return false;
}

char getColor(){
    return color;
}

char getType(){
   return type;
}
}

class KingPiece extends Piece{
KingPiece(char color, char type) {
    super(color, type);
}
public boolean canMove(ChessBoard board,
                       int startRow, int startCol, int endRow, int endCol){
  int rowDelta=Math.abs(endRow-startRow);
  int colDelta=Math.abs(endCol-startCol);
  return rowDelta<=1 && colDelta<=1;
}
}

class KnightPiece extends Piece{
KnightPiece(char color, char type) {
    super(color, type);
}
public boolean canMove(ChessBoard board,
                       int startRow, int startCol, int endRow, int endCol){
    int rowDelta=Math.abs(endRow-startRow);
    int colDelta=Math.abs(endCol-startCol);
    return (rowDelta==2&&colDelta==1) || (rowDelta==1&&colDelta==2);
}
}

class PawnPiece extends Piece{
PawnPiece(char color, char type) {
    super(color, type);
}
public boolean canMove(ChessBoard board,
     int startRow, int startCol, int endRow, int endCol){

  Piece pawn=board.getPiece(startRow, startCol);
  if(board.getPiece(endRow, endCol)==null){
      if(startCol!=endCol) return false;
      return (pawn.getColor()=='W' && endRow-startRow==1)
             ||  (pawn.getColor()=='B' && endRow-startRow==-1);
  }
  else{
      if(Math.abs(startCol-endCol)!=1) return false;
      return (pawn.getColor()=='W' && endRow-startRow==1)
              ||  (pawn.getColor()=='B' && endRow-startRow==-1);
  }
}
}

class DiagonalMove implements Move{
public boolean canMove(ChessBoard board, int startRow,
                       int startCol, int endRow, int endCol) {
    int rowDelta=endRow-startRow;
    int colDelta=endCol-startCol;
    if(Math.abs(rowDelta)!=Math.abs(colDelta)) return false;
    colDelta=colDelta>0?1:-1;
    rowDelta=rowDelta>0?1:-1;
    while(startRow!=endRow){
        if(board.getPiece(startRow,startCol)!=null) return false;
        startRow+=rowDelta;
        startCol+=colDelta;
    }
    return true;
}
}

class StraightMove implements Move{
public boolean canMove(ChessBoard board, int startRow,
                       int startCol, int endRow, int endCol) {
    int rowDelta=endRow-startRow;
    int colDelta=endCol-startCol;
    if(endRow!=startRow && endCol!=startCol) return false;

    if(startRow==endRow){
      colDelta=colDelta>0?1:-1;
      startCol+=colDelta;
      while(startCol!=endCol){
          if(board.getPiece(startRow, startCol)!=null) return false;
          startCol+=colDelta;
      }
    }
    if(startCol==endCol){
        rowDelta=rowDelta>0?1:-1;
        startRow+=rowDelta;
        while(startRow!=endRow){
            if(board.getPiece(startRow, startCol)!=null) return false;
            startRow+=rowDelta;
        }
    }
    return true;
}
}

// uncomment below code in case you are using your local ide and
// comment it back again back when you are pasting completed solution in the online CodeZym editor
// this will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
/**
interface Q08ChessBoardInterface {
void init(Helper08 helper, String[][] chessboard);
String move(int startRow, int startCol, int endRow, int endCol);
int getGameStatus();
int getNextTurn();
}

class Helper08{
void print(String s){System.out.print(s);} void println(String s){print(s+"\n");}
}
*/
```
