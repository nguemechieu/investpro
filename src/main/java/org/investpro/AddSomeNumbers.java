

public class AddSomeNumbers {
    public int addTwoNumbers(int num1, int num2){
        return num1 + num2;
    }
    public static void main(String[] args) {
        AddSomeNumbers addSomeNumbers = new AddSomeNumbers();
        int sum = addSomeNumbers.addTwoNumbers(404,204);
        System.out.println(sum);
    }
}