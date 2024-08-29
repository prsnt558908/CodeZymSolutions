
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