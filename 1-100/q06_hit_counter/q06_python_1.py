
def some_method(text):
    return "some method runs "+text


class SampleClass:
    def method1(self):
     print("Hello from SampleClass method1")
     return "what are you waiting for another day another dawn"
    
    def method2(self, num):
     print("Hello from method-2 ", num)
     print(Ball().play("cosco "))
     print(Ball.playStatic("khanna static"))
     print(some_method("kya kahein"))
     return num*2+1
    
     
class Ball:
     def play(self, text):
        return "playing with ball from Ball() class "+ text 
  
     @staticmethod
     def playStatic(text):
        return "static playing "+text  

