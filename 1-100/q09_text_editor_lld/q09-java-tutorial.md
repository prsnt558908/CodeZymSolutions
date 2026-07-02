# Low Level Design of a Text Editor / Word Processor in Java

Problem Statement:
https://codezym.com/question/9-design-text-editor-word-processor

In this solution, we design a simple text editor where text is stored
row by row. Each row contains characters, and every character has
styling information like font name, font size, bold, and italic. The
core idea is to use the **Flyweight Design Pattern** so that repeated
characters with the same style are not stored as separate objects again
and again. Instead, we reuse the same shared object using a factory.

## Design Overview

The text editor supports four main operations:

1.  Add a character at a given row and column
2.  Read a complete line
3.  Get the style of a character
4.  Delete a character

To keep the design simple, we divide the code into a few important
classes.

## Important Classes

### Solution

This is the main class used by the online judge.

It maintains: - A list of text rows. - A flyweight factory to reuse
styled character objects.

Whenever a character is added, the `Solution` class asks the factory to
create or reuse a `CharFlyweight` object.

### TextRow

Represents one row of text and stores characters in an `ArrayList`.

Responsibilities: - Insert characters - Delete characters - Read a
complete row - Access a character by column

### CharFlyweightFactory

Implements the **Flyweight Design Pattern**.

Instead of creating duplicate style objects, it stores already-created
objects inside a `HashMap` and returns the existing one whenever
possible.

### CharFlyweight

Stores: - Character - Font name - Font size - Bold flag - Italic flag

These objects are shared between multiple positions in the editor
whenever their style is identical.

## Why Flyweight?

Without Flyweight, every character would contain its own copy of the
style information.

With Flyweight: - Lower memory usage - Shared immutable style objects -
Cleaner object-oriented design

## Complexity

  Operation          Time
  ------------------ ------
  Add Character      O(n)
  Delete Character   O(n)
  Read Line          O(n)
  Get Style          O(1)

## Java Code

``` java
import java.util.*;

public class Solution
        implements Q09TextEditorInterface{
    private Helper09 helper;
    private CharFlyweightFactory factory
            = new CharFlyweightFactory();
    private ArrayList<TextRow> rows= new ArrayList<>();

    public Solution(){}
    public void init(Helper09 helper) {
        this.helper=helper;
    }

    public void addCharacter(int row, int column,
                             char ch, String fontName, int fontSize,
                             boolean isBold, boolean isItalic) {
        while(row>=rows.size())rows.add(new TextRow());
        CharFlyweight flyweight=factory.createStyle(
                ch, fontName, fontSize, isBold, isItalic);
        rows.get(row).addCharacter(flyweight, column);
    }

    public String getStyle(int row, int col) {
        if(row<0||row>=rows.size())  return "";
        CharFlyweight flyweight=rows.get(row).getFlyweight(col);
        return flyweight==null?"":flyweight.getCharAndStyle();
    }

    public String readLine(int row) {
        if(row<0||row>=rows.size()) return "";
        List<CharFlyweight> flyweights=rows.get(row).readLine();
        char ch[]=new char[flyweights.size()];
        for(int i=0;i<flyweights.size();i++)
            ch[i]=flyweights.get(i).getChar();
        return new String(ch);
    }

    public boolean deleteCharacter(int row, int col) {
        if(row<0||row>=rows.size()) return false;
        return rows.get(row).deleteCharacter(col);
    }
}

class TextRow{
    private ArrayList<CharFlyweight> data=new ArrayList<>();

    public void addCharacter(CharFlyweight ch,int column){
        data.add(ch);
        int current=data.size()-1;
        while(current>0 && current>column){
            CharFlyweight temp=data.get(current-1);
            data.set(current-1,data.get(current));
            data.set(current,temp);
            current--;
        }
    }

    public CharFlyweight getFlyweight(int column){
        if(column<0||column>=data.size()) return null;
        return data.get(column);
    }

    public List<CharFlyweight> readLine(){
        return data;
    }

    public boolean deleteCharacter(int col){
        if(col<0||col>=data.size()) return false;
        data.remove(col);
        return true;
    }
}

class CharFlyweightFactory{
    private HashMap<String,CharFlyweight> map=new HashMap<>();

    CharFlyweight createStyle(char ch,String fontName,int fontSize,
                              boolean isBold,boolean isItalic){
        StringBuilder sb=new StringBuilder();
        sb.append(ch).append('-').append(fontName)
          .append('-').append(fontSize);
        if(isBold) sb.append("-b");
        if(isItalic) sb.append("-i");

        String key=sb.toString();

        if(!map.containsKey(key)){
            map.put(key,new CharFlyweight(
                    ch,fontName,fontSize,isBold,isItalic));
        }
        return map.get(key);
    }
}

class CharFlyweight{
    private char ch;
    private String fontName;
    private int fontSize;
    private boolean isBold,isItalic;

    CharFlyweight(char ch,String fontName,int fontSize,
                  boolean isBold,boolean isItalic){
        this.ch=ch;
        this.fontName=fontName;
        this.fontSize=fontSize;
        this.isBold=isBold;
        this.isItalic=isItalic;
    }

    char getChar(){
        return ch;
    }

    String getCharAndStyle(){
        StringBuilder sb=new StringBuilder();
        sb.append(ch).append('-').append(fontName)
          .append('-').append(fontSize);
        if(isBold) sb.append("-b");
        if(isItalic) sb.append("-i");
        return sb.toString();
    }
}
```

## Final Thoughts

This solution cleanly separates text storage from style storage. Using
the Flyweight Pattern avoids creating duplicate style objects, making
the design memory-efficient and easy to understand during low-level
design interviews.
