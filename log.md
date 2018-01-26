# 16/1/18
X latex - voor latexila gegaan dit keer.


###presentatie 'categories in the wild'

1. voorbeeld met interface voor externe dingen
2. 'monades' voor non blocking i.o., async, errorhandling etc.
Eigenlijk concreet, de wiskunde zit onder de oppervlakte:
- begin met containertype
- functor (one call)
- applicative (small numbers of calls) `(X->F(X),F(A->B)->F(A)->F(B)//coherence)`
  + traversing (collections of parallel calls)
- monad
  + folding (collections of sequential calls)
  `foldM(f:(X,Y) => F[Y]):(Traversable[X],Y) => F[Y]`
  + tail call recursion
  `tailRec(f:X => F[Either[X,Y]]):X => F[Y]`
3. implementaties/opties:
   - futures
   - CPS / delimited continuations
     `(T=>Unit)=>Unit / [P](T=>P)=>P / [P,Q](T=>P)=>Q`
   - Free monad
   - generiek


Praktische doel: scheiden van business logica en technische implementatie
Welke rol spelen categorieën precies?
-> specificatie
-> generalisatie


Je kunt eerst de abstracties noemen en geleidelijk aan hun rol uitleggen aan de hand van het probleem.

Functor, Applicative, Monad


-> idea: take the common problem of separating business logic from technical implementation
and show where concepts of category come in as part of the problem.
-> if it doesn't look like magic is happening, then I am doing a good job.
   category theory is not magic.

-> important points: mathematicians like DRY as well. Prove theorems about generic monads rather than about each one individually
-> for programming: solve a problem for a generic monad, rather than solving it for a specific one.
That being said, we have to take the limitations of Scala/JVM in account.

###alternatief
talk about redefining composition at the start...

let's not. that was another idea for another time.

###technical problems
Maar natuurlijk vereist latex beamer allerlei leuke add-ons, die ik niet kan installeren vanwege de rottige internetverbinding hier.


### outline

Categories are like cats. You can quickly learn what they are, but that doesn't mean you understand them or can work with them without getting hurt...

- Before I became a software engineer, I used to do research in mathematical logic using category theory. I had little reason to expect to find category theory out here in the wild, but mathematics is everywhere, even "general abstract nonsense"--as category theory is also known among mathematicians.
- This talk is about the practical problem of separating business logic from technical implementation. The solution involves 'applicative functors' and 'monads' from category theory. These abstract concepts help to describe generic solutions.

--- Set up ---
- In order to separate business logic from technical implementation, we define an interface (or 'trait' in Scala) to go between them. The interface describes the process of storing and fetching data in the database e.g. although it could hide many kinds of service.
- There are severe limits on the technical details interfaces/traits can hide, however:
  1 method calls and return values are handled on the same thread.
  2 methods return at most once.
  3 all failures pass through the caller of the interface.
Imagine the service is connected reactive stream, but the business logic for handling each element is the same. There are failure that should be handled in the business logic (service unavailable), but there are also failures that don't (stack overflow). A simple interface means that technical details about concurrency, multiple values and technical errors must be handled on the business end.
- The following solution should be familiar. Wrap the return types with a container type, with methods to pass callback functions. The technical implementation now controls the flow of the program and can implement non blocking io, reuse the same callback when multiple values come in and respond to technical errors without the business being aware of any of it.
- Here is where categories come in. Behaviors of methods that handle callback functions are know in category theory as the 'coherence laws' that define functors, applicatives, monads etc.
- Note that categories have other applications as well, but those aren't in this talk.

--- Categorical methods ---
[map] The simplest is `map:F[X] => (X => Y) =>  F[Y]`. This method applies a function to a single call to the service. To live up to that expectation, it should satisfy `map(y)(x => x) == y` and `map(f)(map(g)(y)) = map(x => f(g(x))(y)` (that is, most of the time, for a suitable notion of equivalence that is never as rigourously defined as a mathematician wants.) The presence of a method that satisfies these equations makes `F` a 'functor'. (Vorsicht Funktor)
[zip] `map` stands for using the service only once. If we want to combine data from more calls, we need `zip:(F[X],F[Y]) => F[(X,Y)]`. Note that there is a map in the opposite direction:
`y => (y.map(_._1),y.map(_._2): F[(X,Y)] => (F[X],F[Y])` 
If `zip` is its inverse '`F` preserves binary products up to isomorphism'.
[unit] With `map` and `zip` the number of calls to external service is fixed at compile time. A method `unit: X => F[X]` give a way into the functor, so that ordinary values can stand in form those that result form an external call. We expect that `map(unit(x))(f) == lift(f(x))`, which makes `F` a pointed functor.
[traverse] We could use `zip`, `map` and `unit` recursively to combine data from a collection of service calls. This comes with a high risk of stack overflow in Scala (and other languages), so in practice we have to add `traverse: T[X] => (X => F[Y]) => F[T[Y]]` for stack safely. 
`def traverse(t:List[X])(f:X => F[Y]) = t.foldLeft(lift(T.empty[X]))((x,y) => zip(f(x),y).map(_ :: _))`
[bind] At this point, we miss the ability to use the data from one call and use it to make another. Here a new method `bind: F[X] => (X => F[Y]) => F[Y]` comes in. The specs are:
- `bind(fx)(lift) == fx`
- `bind(lift(x))(f) == f(x)`
- `bind(bind(fx)(f),g)) == bind(fx,(x: X) => bind(f(x))(g))`
With just `unit` and `bind`, we can replace `map` and `zip`:
- `map(fx)(f) == bind(fx)((x:X) => lift(f(x)))`
- `zip(f,g) == bind(f,x => map(g,y => (x,y))`
Together, this structure is a 'monad'
[...] Of course, the problems with the stack pop up if you want to do a collection of sequential calls, so in this case we need some helpers to. I have not seen any practical application that require this kind of power.

--- Implementations ---
I will run through some examples of container types.
[Future] In gCalendarsAPI we meanly use the futures implementation of `scala.concurrent.Future`. While its purpose is to run computations asynchronously in a thread pool, all of the categorical methods are present and they usually satisfy the specifications close enough.
- it is a standard library for scala, and similar constructs exists in other languages makes them accessible
- where futures deviate form the specs, a lot of nasty bugs can happen. e.g. wrapping blocking io in a future can lead to thread starvation, unless you let the framework know the computation is blocking.
[CPS] continuation passed style is in a sense the 'final' solution to the problem. continuation passing essentially means passing a call back, but we easily implement a monad with the appropriate structure:
...
There are some variation where the continuation have retsurn types etc. etc.
[Free Monad] 
The 'free' in free monad has a categorical specification that is impossible to satsify in a stack safe way in Scala. We can create a free structure that also supports some extra methods for iteration, traversing and folding over large collections. The implementation 'reifies' the methods we shown above.
...
[Generic Monad] With Scala's higher kinded type support, we can turn a monad into a dependency that can be injected into the business logic. This way we can change monad implementations, or use different implementations during tests.

--- Conclusion ---
Categories show up in the wild as 'functional design patterns' that help to solve very practical problems.




