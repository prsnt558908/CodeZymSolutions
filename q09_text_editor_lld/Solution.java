
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

        // if row is more than current row simply append
        // more rows. If column is more than columns in row
        // then simply append character at last
        public void addCharacter(int row, int column,
                                 char ch, String fontName, int fontSize,
                                 boolean isBold, boolean isItalic) {
            while(row>=rows.size())rows.add(new TextRow());
            CharFlyweight flyweight=factory.createStyle(
                    ch, fontName, fontSize, isBold, isItalic);
            rows.get(row).addCharacter(flyweight, column);
        }

        // return "k-Tahoma-22-b-i" or "j-algerian-8-i"
        public String getStyle(int row, int col) {
            if(row<0||row>=rows.size())  return "";
            CharFlyweight flyweight=  rows.get(row).getFlyweight(col);
            return flyweight==null?"":flyweight.getCharAndStyle();
        }

        // e.g."what are you waiting for"
        public String readLine(int row) {
            if(row<0||row>=rows.size()) return "";
            List<CharFlyweight> flyweights= rows.get(row).readLine();
            char ch[]=new char[flyweights.size()];
            for(int i=0;i<flyweights.size();i++)
                ch[i]=flyweights.get(i).getChar();
            return new String(ch);
        }

        // returns true if a character is deleted or false if no character
        public boolean deleteCharacter(int row, int col) {
            if(row<0||row>=rows.size()) return false;
            return rows.get(row).deleteCharacter(col);
        }
    }

    class TextRow{
        private ArrayList<CharFlyweight> data=new ArrayList<>();

        public void addCharacter(CharFlyweight ch, int column){
           data.add(ch);
           int current=data.size()-1;
           while(current>0 && current>column){
               CharFlyweight temp=data.get(current-1);
               data.set(current-1, data.get(current));
               data.set(current, temp);
               current--;
           }
        }

        public CharFlyweight getFlyweight(int column) {
            if(column<0||column>=data.size()) return null;
            return data.get(column);
        }
        
        public List<CharFlyweight> readLine() {
            return data;
        }
        
        public boolean deleteCharacter(int col) {
            if(col<0||col>=data.size()) return false;
            data.remove(col);
            return true;
        }
    }

    class CharFlyweightFactory {
        private HashMap<String, CharFlyweight> map= new HashMap<>();

        CharFlyweight createStyle(char ch, String fontName, int fontSize,
                        boolean isBold, boolean isItalic){
             StringBuilder sb = new StringBuilder();
             sb.append(ch).append('-').append(fontName)
               .append('-').append(fontSize);
             if(isBold)sb.append('-').append('b');
             if(isItalic) sb.append('-').append('i');
             String key = sb.toString();
             if(!map.containsKey(key)) {
                 map.put(key, new CharFlyweight(ch,
                         fontName, fontSize, isBold, isItalic));
             }
             return map.get(key);
        }
    }

    class CharFlyweight{
        private char ch;
        private String fontName;
        private int fontSize;
        private boolean isBold, isItalic;
        CharFlyweight(char ch, String fontName, int fontSize,
                       boolean isBold, boolean isItalic){
         this.ch=ch;
         this.fontName=fontName;
         this.fontSize=fontSize;
         this.isBold=isBold;
         this.isItalic=isItalic;
       }
       
       char getChar(){
            return ch;
       }
       // e.g. "k-Tahoma-22-b-i"
       String getCharAndStyle(){
           StringBuilder sb = new StringBuilder();
           sb.append(ch).append('-').append(fontName)
                   .append('-').append(fontSize);
           if(isBold)sb.append('-').append('b');
           if(isItalic) sb.append('-').append('i');
           return sb.toString();
       }
    }
    


    // uncomment below code when you are using your local code editor and
    // comment it back again back when you are pasting completed solution in the online CodeZym editor
    // this will help avoid unwanted compilation errors and get method autocomplete in your local code editor.
    /**
    interface Q09TextEditorInterface{
      void init(Helper09 helper);
      void addCharacter(int row, int column, char ch, String fontName,
                        int fontSize, boolean isBold, boolean isItalic);
      String getStyle(int row, int col);
      String readLine(int row);
      boolean deleteCharacter(int row, int col);
    }
    class Helper09{
        void print(String s){System.out.print(s);} void println(String s){print(s+"\n");}
    }
    */